package com.burtbeckwith.grails.plugin.datasources

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.orm.hibernate.validation.AbstractPersistentConstraint
import org.codehaus.groovy.grails.orm.hibernate.validation.PersistentConstraint
import org.codehaus.groovy.grails.orm.hibernate.validation.UniqueConstraint
import org.codehaus.groovy.grails.validation.Constraint
import org.hibernate.Hibernate
import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate3.HibernateTemplate
import org.springframework.validation.Errors

class DatasourcesUniqueConstraintFactory {

	private static Map<String, String> _datasourceMap
	private static Map<String, String> _applicationContextMap

	private static final ThreadLocal<String> DATA_SOURCE_NAME = new ThreadLocal<String>()

	static Constraint build() {
		def realConstraint = new DatasourcesUniqueConstraint()
		def invoke = { proxy, Method method, Object[] args ->

			boolean validate = 'validate' == method.name
			if (validate) {
				String targetClassName = Hibernate.getClass(args[0]).name
				DATA_SOURCE_NAME.set _datasourceMap[targetClassName]
				realConstraint.applicationContext = _applicationContextMap[targetClassName]
			}

			try {
				method.invoke realConstraint, args
			}
			finally {
				if (validate) {
					DATA_SOURCE_NAME.remove()
					realConstraint.applicationContext = null
				}
			}
		}

		Proxy.newProxyInstance Constraint.classLoader, [PersistentConstraint] as Class[],
			[invoke: invoke] as InvocationHandler
	}

	static void setDatasourceMap(Map<String, String> map) {
		_datasourceMap = map
	}

	static void setApplicationContextMap(Map<String, String> map) {
		_applicationContextMap = map
	}
}

class DatasourcesUniqueConstraint extends UniqueConstraint {
	@Override
	HibernateTemplate getHibernateTemplate() {
		DatasourcesUtils.newHibernateTemplate DatasourcesUniqueConstraintFactory.DATA_SOURCE_NAME.get()
	}
}
