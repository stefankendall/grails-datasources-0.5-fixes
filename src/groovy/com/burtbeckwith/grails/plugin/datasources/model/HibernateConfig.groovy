package com.burtbeckwith.grails.plugin.datasources.model

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class HibernateConfig extends Expando {

	CacheConfig cache = new CacheConfig()

	String toString() { toProperties().toString() }

	Properties toProperties() {
		Properties props = new Properties()
		props.putAll properties

		cache.properties.each { key, value ->
			if (value) {
				props.setProperty "hibernate.cache.$key", value.toString()
			}
		}

		return props
	}
}
