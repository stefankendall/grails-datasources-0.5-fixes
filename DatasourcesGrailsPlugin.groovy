import javax.sql.DataSource

import grails.util.GrailsUtil

import org.apache.commons.dbcp.BasicDataSource

import org.codehaus.groovy.grails.commons.ApplicationHolder as AH
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.exceptions.GrailsConfigurationException
import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.codehaus.groovy.grails.orm.hibernate.support.GrailsOpenSessionInViewInterceptor
import org.codehaus.groovy.grails.orm.hibernate.support.SpringLobHandlerDetectorFactoryBean
import org.codehaus.groovy.grails.orm.hibernate.validation.HibernateDomainClassValidator
import org.codehaus.groovy.grails.orm.hibernate.validation.UniqueConstraint
import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.codehaus.groovy.grails.validation.Constraint
import org.codehaus.groovy.grails.validation.ConstraintFactory
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsUrlHandlerMapping

import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.nativejdbc.CommonsDbcpNativeJdbcExtractor
import org.springframework.jndi.JndiObjectFactoryBean
import org.springframework.orm.hibernate3.HibernateAccessor
import org.springframework.orm.hibernate3.HibernateCallback
import org.springframework.orm.hibernate3.HibernateTemplate
import org.springframework.orm.hibernate3.HibernateTransactionManager
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean
import org.springframework.util.ReflectionUtils

import com.burtbeckwith.grails.plugin.datasources.AggregatePersistenceContextInterceptor
import com.burtbeckwith.grails.plugin.datasources.DatasourcesBuilder
import com.burtbeckwith.grails.plugin.datasources.DatasourcesUtils
import com.burtbeckwith.grails.plugin.datasources.DatasourcesUniqueConstraintFactory
import com.burtbeckwith.grails.plugin.datasources.FakeApplicationContext
import com.burtbeckwith.grails.plugin.datasources.FakeConfigurableLocalSessionFactoryBean
import com.burtbeckwith.grails.plugin.datasources.FakeGrailsApplication
import com.burtbeckwith.grails.plugin.datasources.ReadOnlyDriverManagerDataSource

class DatasourcesGrailsPlugin {

	String version = '0.5.1'
	Map dependsOn = [hibernate: '1.3.7 > *']
	List loadAfter = ['hibernate', 'services']
	String watchedResources = 'file:./grails-app/conf/Datasources.groovy'
	String author = 'Burt Beckwith'
	String authorEmail = 'burt@burtbeckwith.com'
	String title = 'Grails Datasources Plugin.'
	String description = 'Supports defining multiple data sources per application.'
	String documentation = 'http://grails.org/plugin/datasources'

	def doWithSpring = {

		def dataSources = loadConfig()
		if (!dataSources) {
			return
		}

		def handlerInterceptors = []
		if (manager?.hasGrailsPlugin('controllers')) {
			handlerInterceptors = [ref('localeChangeInterceptor'), ref('openSessionInViewInterceptor')]
		}

		def application = AH.application

		// replace the 'persistenceInterceptor' bean with one that has an interceptor for each datasource
		def persistenceContextDataSourceNames = []
		persistenceInterceptor(AggregatePersistenceContextInterceptor) {
			dataSourceNames = persistenceContextDataSourceNames
		}

		for (ds in dataSources) {

			if (!ds.environments.contains(GrailsUtil.environment)) {
				continue
			}

			String name = ds.name
			Class dsClass = ds.jndiName ? JndiObjectFactoryBean :
				ds.pooled ? BasicDataSource :
				ds.readOnly ? ReadOnlyDriverManagerDataSource : DriverManagerDataSource

			def dsBean = "dataSource_$name"(dsClass) {
				if (ds.jndiName) {
					jndiName = ds.jndiName
					expectedType = DataSource
				}
				else {
					if (ds.pooled) {
						defaultReadOnly = ds.readOnly
					}
					driverClassName = ds.driverClassName
					url = ds.url
					if (ds.username) {
						username = ds.username
					}

					if (ds.password)  {
						String thePassword = ds.password
						if (ds.passwordEncryptionCodec) {
							def encryptionCodec = ds.passwordEncryptionCodec
							if (encryptionCodec instanceof Class) {
								try {
									password = encryptionCodec.decode(thePassword)
								}
								catch (e) {
									throw new GrailsConfigurationException("Error decoding dataSource password with codec [$encryptionCodec]: ${e.message}", e)
								}
							}
							else {
								encryptionCodec = encryptionCodec.toString()
								def codecClass = application.codecClasses.find { it.name?.equalsIgnoreCase(encryptionCodec) || it.fullName == encryptionCodec}?.clazz
								try {
									if (!codecClass) {
										codecClass = application.classLoader.loadClass(encryptionCodec)
									}
									if (codecClass) {
										password = codecClass.decode(thePassword)
									}
									else {
										throw new GrailsConfigurationException("Error decoding dataSource password. Codec class not found for name [$encryptionCodec]")
									}
								}
								catch (ClassNotFoundException e) {
									throw new GrailsConfigurationException("Error decoding dataSource password. Codec class not found for name [$encryptionCodec]: $e.message", e)
								}
								catch (e) {
									throw new GrailsConfigurationException("Error decoding dataSource password with codec [$encryptionCodec]: ${e.message}", e)
								}
							}
						}
						else {
							password = ds.password
						}
					}
				}
			}
			if (ds.pooled && !ds.jndiName) {
				dsBean.destroyMethod = 'close'
			}

			"hibernateProperties_$name"(PropertiesFactoryBean) { bean ->
				bean.scope = 'prototype'
				properties = ds.hibernateProperties
			}
			if (GrailsUtil.grailsVersion.startsWith('1.0')) {
				"lobHandlerDetector_$name"(SpringLobHandlerDetectorFactoryBean) {
					dataSource = ref("dataSource_$name")
				}
			}
			else {
				nativeJdbcExtractor(CommonsDbcpNativeJdbcExtractor)
				"lobHandlerDetector_$name"(SpringLobHandlerDetectorFactoryBean) {
					dataSource = ref("dataSource_$name")
					pooledConnection = ds.pooled ?: false
					nativeJdbcExtractor = ref('nativeJdbcExtractor')
				}
			}

			def domainClasses = findDataSourceDomainClasses(ds, application)
			"sessionFactory_$name"(FakeConfigurableLocalSessionFactoryBean) {
				dataSource = ref("dataSource_$name")

				if (application.classLoader.getResource("${name}_hibernate.cfg.xml")) {
					configLocation = "classpath:${name}_hibernate.cfg.xml"
				}

				hibernateProperties = ref("hibernateProperties_$name")
				grailsApplication = new FakeGrailsApplication(domainClasses, ds)
				lobHandler = ref("lobHandlerDetector_$name")
				if (GrailsUtil.grailsVersion.startsWith('1.0')) {
					entityInterceptor = ref('eventTriggeringInterceptor')
				}
				else {
					eventListeners = [
						'pre-load': eventTriggeringInterceptor,
						'post-load': eventTriggeringInterceptor,
						'save': eventTriggeringInterceptor,
						'save-update': eventTriggeringInterceptor,
						'post-insert': eventTriggeringInterceptor,
						'pre-update': eventTriggeringInterceptor,
						'post-update': eventTriggeringInterceptor,
						'pre-delete': eventTriggeringInterceptor,
						'post-delete': eventTriggeringInterceptor]
				}
			}

			"transactionManager_$name"(HibernateTransactionManager) {
				sessionFactory = ref("sessionFactory_$name")
			}

			persistenceContextDataSourceNames << name

			if (manager?.hasGrailsPlugin('controllers')) {
				"openSessionInViewInterceptor_$name"(GrailsOpenSessionInViewInterceptor) {
					if (ds.readOnly) {
						flushMode = HibernateAccessor.FLUSH_NEVER
					}
					else {
						if (ds.flushMode) {
							switch (ds.flushMode) {
								case 'manual': flushMode = HibernateAccessor.FLUSH_NEVER; break
								case 'always': flushMode = HibernateAccessor.FLUSH_ALWAYS; break
								case 'commit': flushMode = HibernateAccessor.FLUSH_COMMIT; break
								default:       flushMode = HibernateAccessor.FLUSH_AUTO
							}
						}
						else {
							flushMode = HibernateAccessor.FLUSH_AUTO
						}
					}

					sessionFactory = ref("sessionFactory_$name")
				}

				handlerInterceptors << ref("openSessionInViewInterceptor_$name")
			}

			// configure transactional services
			if (ds.services) {
				for (serviceClass in application.serviceClasses) {
					if (serviceClass.transactional &&
							ds.services.contains(serviceClass.propertyName - 'Service')) {

						def scope = serviceClass.getPropertyValue("scope")
						def props = new Properties()
						String attributes = 'PROPAGATION_REQUIRED'
						if (ds.readOnly) {
							attributes += ',readOnly'
						}
						props."*" = attributes
						"${serviceClass.propertyName}"(TransactionProxyFactoryBean) { bean ->
						if (scope) bean.scope = scope
							target = { innerBean ->
								innerBean.factoryBean = "${serviceClass.fullName}ServiceClass"
								innerBean.factoryMethod = "newInstance"
								innerBean.autowire = "byName"
								if (scope) innerBean.scope = scope
							}
							proxyTargetClass = true
							transactionAttributes = props
							transactionManager = ref("transactionManager_$name")
						}
					}
				}
			}
		}

		// redefine with the extra OSIV interceptors
		if (GrailsUtil.grailsVersion.startsWith('1.0') || GrailsUtil.grailsVersion.startsWith('1.1')) {
			grailsUrlHandlerMapping(GrailsUrlHandlerMapping) {
				interceptors = handlerInterceptors
				mappings = ref('grailsUrlMappings')
			}
		}
		else { // 1.2 or higher

			// allow @Controller annotated beans
			annotationHandlerMapping(org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping) {
				interceptors = handlerInterceptors
			}
			// allow default controller mappings
			controllerHandlerMappings(org.codehaus.groovy.grails.web.servlet.GrailsControllerHandlerMapping) {
				interceptors = handlerInterceptors
			}
		}
	}

	private findDataSourceDomainClasses(ds, application) {
		Set classNames = ds.domainClasses
		def domainClasses = application.domainClasses.findAll { dc ->
			// need to check name in case of app reload
			classNames.contains(dc.clazz.name)
		}
		return domainClasses as Set
	}

	def doWithDynamicMethods = { ctx ->

		def application = AH.application
		def dataSources = loadConfig()
		if (!dataSources) {
			return
		}

		DatasourcesUtils.applicationContext = ctx
		DomainClassArtefactHandler artefactHandler = new DomainClassArtefactHandler()
		
		def datasourceNameByDomainClass = [:]
		def applicationContextByDomainClass = [:]
		for (ds in dataSources) {

			if (!ds.environments.contains(GrailsUtil.environment)) {
				continue
			}

			String name = ds.name
			def sessionFactoryBean = ctx.getBean("&sessionFactory_$name")
			def fakeApplication = sessionFactoryBean.grailsApplication
			def domainClasses = fakeApplication.domainClasses
			def sessionFactory = ctx.getBean("sessionFactory_$name")
			def fakeApplicationContext = new FakeApplicationContext(
					transactionManager: ctx.getBean("transactionManager_$name"),
					sessionFactory: sessionFactory,
					grailsApplication: fakeApplication)

			if (GrailsUtil.grailsVersion.startsWith('1.0')) {
				Class clazz = application.classLoader.loadClass('org.codehaus.groovy.grails.plugins.orm.hibernate.HibernateGrailsPlugin')
				def closure = clazz.newInstance().doWithDynamicMethods
				closure.delegate = [application: fakeApplication]
				closure.call fakeApplicationContext
			}
			else {
				Class domainClassGrailsPlugin = application.classLoader.loadClass('org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin')
				domainClassGrailsPlugin.enhanceDomainClasses(fakeApplication, fakeApplicationContext)

				Class hibernatePluginSupport = application.classLoader.loadClass('org.codehaus.groovy.grails.plugins.orm.hibernate.HibernatePluginSupport')
				hibernatePluginSupport.enhanceSessionFactory sessionFactory, fakeApplication, fakeApplicationContext
			}

			initializeDomainClasses fakeApplication, application, artefactHandler, domainClasses
			GrailsHibernateUtil.configureHibernateDomainClasses(sessionFactory, application)

			// post registration hook for any additional dynamic methods we wish to inject into the domainclasses for this datasource
			for (dc in domainClasses) {
				def metaClass = dc.metaClass
				// add a withSession that allows the datasource name to be exlicitly specified
				// (useful if you have configured the same domainclasses with multiple datasources)
				metaClass.'static'.withExplicitSession = { String dataSourceName, Closure callable ->
					DatasourcesUtils.newHibernateTemplate(dataSourceName).execute({ session ->
						callable(session)
					} as HibernateCallback)
				}
			}

			// configure the validator's applicationContext so the validator gets the correct sessionFactory
			for (dc in domainClasses) {
				String validatorName = "${dc.fullName}Validator"
				if (!ctx.containsBean(validatorName)) {
					continue
				}
				def validator = ctx.getBean(validatorName)
				if (validator instanceof HibernateDomainClassValidator) {
					validator.applicationContext = fakeApplicationContext
				}
			}

			for (dc in domainClasses) {
				datasourceNameByDomainClass[dc.clazz.name] = name
				applicationContextByDomainClass[dc.clazz.name] = fakeApplicationContext
			}
		}

		// also map domain classes not in a secondary datasource
		for (dc in application.domainClasses) {
			if (!datasourceNameByDomainClass.containsKey(dc.clazz.name)) {
				datasourceNameByDomainClass[dc.clazz.name] = ''
				applicationContextByDomainClass[dc.clazz.name] = ctx
			}
		}
		DatasourcesUniqueConstraintFactory.datasourceMap = datasourceNameByDomainClass
		DatasourcesUniqueConstraintFactory.applicationContextMap = applicationContextByDomainClass
	}

	def doWithApplicationContext = { applicationContext ->
	def dataSources = loadConfig()
        if(dataSources) {
 		ConstrainedProperty.registerNewConstraint(UniqueConstraint.UNIQUE_CONSTRAINT,
 		  [newInstance: { -> DatasourcesUniqueConstraintFactory.build() }] as ConstraintFactory)
		} 
	}

	def doWithWebDescriptor = { xml -> }
	def onChange = { event -> }
	def onConfigChange = { event -> }
	def onShutdown = { event -> }

	private void initializeDomainClasses(fakeApplication, application, artefactHandler, domainClasses) {

		def loadedClassesField = ReflectionUtils.findField(application.class, 'loadedClasses')
		loadedClassesField.accessible = true
		Class[] allClasses = loadedClassesField.get(application)
		def dsLoadedClasses = loadedClassesField.get(fakeApplication)

		for (clazz in allClasses) {
			if (isDsDomainClass(clazz, artefactHandler, domainClasses)) {
				dsLoadedClasses.add clazz
			}
		}
		fakeApplication.initialise()
	}

	private boolean isDsDomainClass(clazz, artefactHandler, domainClasses) {
		if (!artefactHandler.isArtefactClass(clazz)) {
			return false
		}

		for (c in domainClasses) {
			if (c.clazz == clazz) {
				return true
			}
		}

		return false
	}

	private loadConfig() {
		try {
			def script = AH.application.classLoader.loadClass('Datasources').newInstance()
			script.run()
	
			def builder = new DatasourcesBuilder()
			def datasources = script.datasources
			datasources.delegate = builder
			datasources()
	
			return builder.dataSources
		}
		catch (e) {
			println e.message
			return []
		}
	}
}
