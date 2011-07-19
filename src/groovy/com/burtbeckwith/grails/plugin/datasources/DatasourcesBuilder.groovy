package com.burtbeckwith.grails.plugin.datasources

import groovy.util.BuilderSupport

import com.burtbeckwith.grails.plugin.datasources.model.DatasourceConfig

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DatasourcesBuilder extends BuilderSupport {

	def dataSources = []

	private List<String> _stack = []
	private DatasourceConfig _currentDs
	private Closure _hibernateClosure

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#createNode(java.lang.Object)
	 */
	@Override
	protected Object createNode(Object name) {
		switch (name) {
			case 'hibernate':
			case 'cache':
				_stack.push name
				return name
			default:
				throw new IllegalArgumentException("Cannot create empty node with name '$name'")
		}
	}

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#createNode(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected Object createNode(Object name, Object value) {

		String level = _stack[-1]
		_stack.push name

		switch (level) {
			case 'datasource':
				switch (name) {
					case 'name':
					case 'readOnly':
					case 'driverClassName':
					case 'url':
					case 'username':
					case 'password':
					case 'parentDs':
					case 'dbCreate':
					case 'jndiName':
					case 'pooled':
					case 'loggingSql':
					case 'logSql':
					case 'passwordEncryptionCodec':
					case 'flushMode':
						_currentDs."$name" = value
						return name
					case 'dialect':
						_currentDs.dialect = value instanceof Class ? value.name : value
						return name
					case 'domainClasses':
					case 'services':
					case 'environments':
						_currentDs."$name" = toList(value)
						return name
				}
				break
			case 'hibernate':
				// not controlled, any value gets passed through
				_currentDs.hibernate."$name" = value
				return name
			case 'cache':
				// not controlled, any value gets passed through
				_currentDs.hibernate.cache."$name" = value
				return name
		}

		throw new IllegalArgumentException("Cannot create node with name '$name' and value '$value'")
	 }

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#createNode(java.lang.Object, java.util.Map)
	 */
	@Override
	protected Object createNode(Object name, Map attributes) {
		_stack.push name

		if ('datasource' == name) {
			_currentDs = attributes
			dataSources << _currentDs
			return name
		}

		throw new IllegalArgumentException("Cannot create node with name '$name'")
	}

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#createNode(java.lang.Object, java.util.Map, java.lang.Object)
	 */
	@Override
	protected Object createNode(Object name, Map attributes, Object value) {
		throw new UnsupportedOperationException()
	}

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#setParent(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected void setParent(Object parent, Object child) {
		// do nothing
	}

	/**
	 * {@inheritDoc}
	 * @see groovy.util.BuilderSupport#nodeCompleted(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected void nodeCompleted(Object parent, Object node) {
		_stack.pop()
	}

	private List toList(o) {
		return o instanceof List ? o : o?.class?.array ? o : [o]
	}
}
