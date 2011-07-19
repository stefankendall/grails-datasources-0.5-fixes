package com.burtbeckwith.grails.plugin.datasources

import javax.sql.DataSource

import org.hibernate.SessionFactory

import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate3.HibernateTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * Utility methods for datasources.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DatasourcesUtils {

	static ApplicationContext applicationContext

	/**
	 * Create a HibernateTemplate using the specified data source.
	 * @param dsName  the ds name - if null return the default data source
	 * @return  the template
	 */
	static HibernateTemplate newHibernateTemplate(String dsName = null) {
		String name = dsName ? "sessionFactory_$dsName" : 'sessionFactory'
		return new HibernateTemplate(applicationContext.getBean(name))
	}

	/**
	 * Get the named datasource.
	 * @param dsName  the name of the secondary datasource or null for the default
	 * @return  the datasource
	 */
	static DataSource getDataSource(String dsName = null) {
		return getDsBean(dsName, 'dataSource')
	}

	/**
	 * Get the session factory for the datasource.
	 * @param dsName  the name of the secondary datasource or null for the default
	 * @return  the session factory
	 */
	static SessionFactory getSessionFactory(String dsName = null) {
		return getDsBean(dsName, 'sessionFactory')
	}

	/**
	 * Get the transaction manager for the datasource.
	 * @param dsName  the name of the secondary datasource or null for the default
	 * @return  the transaction manager
	 */
	static PlatformTransactionManager getTransactionManager(String dsName = null) {
		return getDsBean(dsName, 'transactionManager')
	}

	private static getDsBean(String dsName, String prefix) {
		return applicationContext.getBean(dsName ? "${prefix}_$dsName" : prefix)
	}
}
