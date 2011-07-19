package com.burtbeckwith.grails.plugin.datasources

import org.codehaus.groovy.grails.commons.ApplicationHolder as AH
import org.codehaus.groovy.grails.orm.hibernate.support.HibernatePersistenceContextInterceptor
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class AggregatePersistenceContextInterceptor implements PersistenceContextInterceptor, InitializingBean, ApplicationContextAware {

	private List<PersistenceContextInterceptor> _interceptors = []
	private List<String> _dataSourceNames = []
	private ApplicationContext _applicationContext

	boolean isOpen() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			if (interceptor.isOpen()) {
				// true at least one is true
				return true
			}
		}
		return false
	}

	void reconnect() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.reconnect()
		}
	}

	void destroy() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			if (interceptor.isOpen()) {
				interceptor.destroy()
			}
		}
	}

	void clear() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.clear()
		}
	}

	void disconnect() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.disconnect()
		}
	}

	void flush() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.flush()
		}
	}

	void init() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.init()
		}
	}

	void setReadOnly() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.setReadOnly()
		}
	}

	void setReadWrite() {
		for (PersistenceContextInterceptor interceptor : _interceptors) {
			interceptor.setReadWrite()
		}
	}

	/**
	 * Dependency injection for the datasource names.
	 * @param names  the names
	 */
	void setDataSourceNames(List<String> names) {
		_dataSourceNames = names
	}

	void afterPropertiesSet() {
		// need to lazily create these instead of registering as beans since Grails
		// looks for instances of PersistenceContextInterceptor and picks one assuming
		// there's only one, so this one has to be the only one
		_interceptors << new HibernatePersistenceContextInterceptor(
				sessionFactory: _applicationContext.sessionFactory)
		for (String name in _dataSourceNames) {
			_interceptors << new HibernatePersistenceContextInterceptor(
					sessionFactory: _applicationContext.getBean("sessionFactory_$name"))
		}
	}

	void setApplicationContext(ApplicationContext applicationContext) {
		_applicationContext = applicationContext
	}
}
