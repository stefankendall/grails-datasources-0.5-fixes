package com.burtbeckwith.grails.plugin.datasources.model

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class CacheConfig extends Expando {

	String toString() {
		return properties.toString()
	}
}
