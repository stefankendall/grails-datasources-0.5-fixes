package com.burtbeckwith.grails.plugin.datasources

import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean

import org.hibernate.HibernateException
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration

/**
 * @author Paul Fisher
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class FakeConfigurableLocalSessionFactoryBean extends ConfigurableLocalSessionFactoryBean {

	@Override
	protected SessionFactory newSessionFactory(Configuration config) throws HibernateException {
		grailsApplication.refresh()
		return super.newSessionFactory(config)
	}
}
