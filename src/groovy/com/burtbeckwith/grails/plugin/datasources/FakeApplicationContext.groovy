package com.burtbeckwith.grails.plugin.datasources

import org.springframework.context.support.GenericApplicationContext

/**
 * Used as a mock when configuring.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class FakeApplicationContext extends GenericApplicationContext {

	def grailsApplication
	def sessionFactory
	def transactionManager

	@Override
	Object getBean(String name) {
		if (name.startsWith('sessionFactory')) {
			return sessionFactory
		}

		if (name.startsWith('transactionManager')) {
			return transactionManager
		}

		if (name.startsWith('grailsApplication')) {
			return grailsApplication
		}

		return null
	}
}
