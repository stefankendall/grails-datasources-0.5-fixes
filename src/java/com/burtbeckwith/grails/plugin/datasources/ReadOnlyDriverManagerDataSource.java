package com.burtbeckwith.grails.plugin.datasources;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
public class ReadOnlyDriverManagerDataSource extends DriverManagerDataSource {

	/**
	 * {@inheritDoc}
	 * @see org.springframework.jdbc.datasource.DriverManagerDataSource#getConnectionFromDriverManager(
	 * 	java.lang.String, java.util.Properties)
	 */
	@Override
	protected Connection getConnectionFromDriverManager(final String url, final Properties props) throws SQLException {
		Connection connection = super.getConnectionFromDriverManager(url, props);
		connection.setReadOnly(true);
		return connection;
	}
}
