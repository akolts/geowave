package mil.nga.giat.geowave.datastore.hbase.operations;

import java.io.IOException;
import java.util.HashMap;

import mil.nga.giat.geowave.core.store.DataStoreOperations;
import mil.nga.giat.geowave.datastore.hbase.io.HBaseWriter;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseRequiredOptions;
import mil.nga.giat.geowave.datastore.hbase.util.ConnectionPool;
import mil.nga.giat.geowave.datastore.hbase.util.HBaseUtils;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutator.ExceptionListener;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.security.visibility.Authorizations;
import org.apache.log4j.Logger;

public class BasicHBaseOperations implements
		DataStoreOperations
{

	private final static Logger LOGGER = Logger.getLogger(BasicHBaseOperations.class);
	private static final String DEFAULT_TABLE_NAMESPACE = "";
	public static final Object ADMIN_MUTEX = new Object();

	private final Connection conn;
	private final String tableNamespace;
	private final HashMap<String, BufferedMutator> mutatorMap;

	public BasicHBaseOperations(
			final String zookeeperInstances,
			final String geowaveNamespace )
			throws IOException {
		conn = ConnectionPool.getInstance().getConnection(
				zookeeperInstances);
		tableNamespace = geowaveNamespace;
		mutatorMap = new HashMap<String, BufferedMutator>();
	}

	public BasicHBaseOperations(
			final String zookeeperInstances )
			throws IOException {
		this(
				zookeeperInstances,
				DEFAULT_TABLE_NAMESPACE);
	}

	public BasicHBaseOperations(
			final Connection connector ) {
		this(
				DEFAULT_TABLE_NAMESPACE,
				connector);
	}

	public BasicHBaseOperations(
			final String tableNamespace,
			final Connection connector ) {
		this.tableNamespace = tableNamespace;
		conn = connector;
		mutatorMap = new HashMap<String, BufferedMutator>();
	}

	public static BasicHBaseOperations createOperations(
			final HBaseRequiredOptions options )
			throws IOException {
		return new BasicHBaseOperations(
				options.getZookeeper(),
				options.getGeowaveNamespace());
	}

	public BufferedMutator getBufferedMutator(
			final String tableName )
			throws IOException {
		if (!mutatorMap.containsKey(tableName)) {
			BufferedMutatorParams params = new BufferedMutatorParams(
					TableName.valueOf(tableName));

			// Just guessing. Default is 2M. Trying 16M here.
			params.writeBufferSize(16L * 1024L & 1024L);

			params.listener(new ExceptionListener() {
				@Override
				public void onException(
						RetriesExhaustedWithDetailsException exception,
						BufferedMutator mutator )
						throws RetriesExhaustedWithDetailsException {
					LOGGER.error(exception);
				}
			});

			BufferedMutator mutator = conn.getBufferedMutator(params);

			mutatorMap.put(
					tableName,
					mutator);
		}

		return mutatorMap.get(tableName);
	}

	public HBaseWriter createWriter(
			final String tableName,
			final String columnFamily )
			throws IOException {
		return createWriter(
				tableName,
				columnFamily,
				true);
	}

	private TableName getTableName(
			final String tableName ) {
		return TableName.valueOf(tableName);
	}

	public HBaseWriter createWriter(
			final String sTableName,
			final String columnFamily,
			final boolean createTable )
			throws IOException {
		BufferedMutator mutator = getBufferedMutator(sTableName);

		if (createTable) {
			createTable(
					columnFamily,
					TableName.valueOf(sTableName));
		}

		return new HBaseWriter(
				conn.getAdmin(),
				sTableName,
				mutator);
	}

	/*
	 * private Table getTable( final boolean create, TableName name ) throws IOException { return getTable( create,
	 * DEFAULT_COLUMN_FAMILY, name); }
	 */

	private Table getTable(
			final boolean create,
			final String columnFamily,
			final String tableName )
			throws IOException {
		TableName name = TableName.valueOf(tableName);

		synchronized (ADMIN_MUTEX) {
			if (create) {
				createTable(
						columnFamily,
						name);
			}
		}

		return conn.getTable(name);
	}

	private void createTable(
			final String columnFamily,
			final TableName name )
			throws IOException {
		synchronized (ADMIN_MUTEX) {
			if (!conn.getAdmin().isTableAvailable(
					name)) {
				final HTableDescriptor desc = new HTableDescriptor(
						name);
				desc.addFamily(new HColumnDescriptor(
						columnFamily));
				conn.getAdmin().createTable(
						desc);
			}
		}
	}

	public String getQualifiedTableName(
			final String unqualifiedTableName ) {
		return HBaseUtils.getQualifiedTableName(
				tableNamespace,
				unqualifiedTableName);
	}

	@Override
	public void deleteAll()
			throws IOException {
		final TableName[] tableNamesArr = conn.getAdmin().listTableNames();
		for (final TableName tableName : tableNamesArr) {
			if ((tableNamespace == null) || tableName.getNameAsString().startsWith(
					tableNamespace)) {
				synchronized (ADMIN_MUTEX) {
					if (conn.getAdmin().isTableAvailable(
							tableName)) {
						conn.getAdmin().disableTable(
								tableName);
						conn.getAdmin().deleteTable(
								tableName);
					}
				}
			}
		}
	}

	@Override
	public boolean tableExists(
			final String tableName )
			throws IOException {
		final String qName = getQualifiedTableName(tableName);
		synchronized (ADMIN_MUTEX) {
			return conn.getAdmin().isTableAvailable(
					getTableName(qName));
		}

	}

	public boolean columnFamilyExists(
			final String tableName,
			final String columnFamily )
			throws IOException {
		final String qName = getQualifiedTableName(tableName);
		synchronized (ADMIN_MUTEX) {
			final HTableDescriptor descriptor = conn.getAdmin().getTableDescriptor(
					getTableName(qName));

			if (descriptor != null) {
				for (final HColumnDescriptor hColumnDescriptor : descriptor.getColumnFamilies()) {
					if (hColumnDescriptor.getNameAsString().equalsIgnoreCase(
							columnFamily)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public ResultScanner getScannedResults(
			final Scan scanner,
			final String tableName,
			final String... authorizations )
			throws IOException {
		if (authorizations != null) {
			scanner.setAuthorizations(new Authorizations(
					authorizations));
		}
		return conn.getTable(
				getTableName(getQualifiedTableName(tableName))).getScanner(
				scanner);
	}

	public boolean deleteTable(
			final String tableName ) {
		final String qName = getQualifiedTableName(tableName);
		try {
			conn.getAdmin().deleteTable(
					getTableName(qName));
			return true;
		}
		catch (final IOException ex) {
			LOGGER.warn(
					"Unable to delete table '" + qName + "'",
					ex);
		}
		return false;

	}

	public RegionLocator getRegionLocator(
			final String tableName )
			throws IOException {
		return conn.getRegionLocator(getTableName(getQualifiedTableName(tableName)));
	}

	@Override
	public String getTableNameSpace() {
		return tableNamespace;
	}
}
