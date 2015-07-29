package mil.nga.giat.geowave.core.store.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.store.CloseableIterator;
import mil.nga.giat.geowave.core.store.DataStore;
import mil.nga.giat.geowave.core.store.DataStoreEntryInfo;
import mil.nga.giat.geowave.core.store.DataStoreEntryInfo.FieldInfo;
import mil.nga.giat.geowave.core.store.IndexWriter;
import mil.nga.giat.geowave.core.store.IngestCallback;
import mil.nga.giat.geowave.core.store.ScanCallback;
import mil.nga.giat.geowave.core.store.adapter.AdapterStore;
import mil.nga.giat.geowave.core.store.adapter.DataAdapter;
import mil.nga.giat.geowave.core.store.adapter.IndexedAdapterPersistenceEncoding;
import mil.nga.giat.geowave.core.store.adapter.MemoryAdapterStore;
import mil.nga.giat.geowave.core.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.core.store.adapter.statistics.DataStatisticsStore;
import mil.nga.giat.geowave.core.store.adapter.statistics.MemoryDataStatisticsStore;
import mil.nga.giat.geowave.core.store.adapter.statistics.StatsCompositionTool;
import mil.nga.giat.geowave.core.store.data.VisibilityWriter;
import mil.nga.giat.geowave.core.store.filter.QueryFilter;
import mil.nga.giat.geowave.core.store.index.Index;
import mil.nga.giat.geowave.core.store.index.IndexStore;
import mil.nga.giat.geowave.core.store.index.MemoryIndexStore;
import mil.nga.giat.geowave.core.store.query.Query;
import mil.nga.giat.geowave.core.store.query.QueryOptions;

public class MemoryDataStore implements
		DataStore
{
	private final static Logger LOGGER = Logger.getLogger(MemoryDataStore.class);
	private final Map<ByteArrayId, TreeSet<EntryRow>> storeData = new HashMap<ByteArrayId, TreeSet<EntryRow>>();
	private final AdapterStore adapterStore;
	private final IndexStore indexStore;
	private final DataStatisticsStore statsStore;

	public MemoryDataStore() {
		super();
		adapterStore = new MemoryAdapterStore();
		indexStore = new MemoryIndexStore();
		statsStore = new MemoryDataStatisticsStore();
	}

	public MemoryDataStore(
			final AdapterStore adapterStore,
			final IndexStore indexStore,
			final DataStatisticsStore statsStore ) {
		super();
		this.adapterStore = adapterStore;
		this.indexStore = indexStore;
		this.statsStore = statsStore;
	}

	@Override
	public <T> IndexWriter createIndexWriter(
			final Index index ) {

		return createWriter(
				index,
				null,
				new IngestCallback<T>() {

					@Override
					public void entryIngested(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				DataStoreUtils.DEFAULT_VISIBILITY);
	}

	@Override
	public <T> List<ByteArrayId> ingest(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final T entry ) {
		return ingestInternal(
				writableAdapter,
				index,
				Collections.singletonList(
						entry).iterator(),
				new IngestCallback<T>() {

					@Override
					public void entryIngested(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				DataStoreUtils.DEFAULT_VISIBILITY);
	}

	@Override
	public <T> void ingest(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final Iterator<T> entryIterator ) {
		ingestInternal(
				writableAdapter,
				index,
				entryIterator,
				new IngestCallback<T>() {

					@Override
					public void entryIngested(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				DataStoreUtils.DEFAULT_VISIBILITY);
	}

	@Override
	public <T> List<ByteArrayId> ingest(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final T entry,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		return ingestInternal(
				writableAdapter,
				index,
				Collections.singletonList(
						entry).iterator(),
				new IngestCallback<T>() {

					@Override
					public void entryIngested(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				customFieldVisibilityWriter);

	}

	@Override
	public <T> void ingest(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final Iterator<T> entryIterator,
			final IngestCallback<T> ingestCallback ) {
		ingest(
				writableAdapter,
				index,
				entryIterator,
				ingestCallback,
				DataStoreUtils.DEFAULT_VISIBILITY);
	}

	@Override
	public <T> void ingest(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final Iterator<T> entryIterator,
			final IngestCallback<T> ingestCallback,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		ingestInternal(
				writableAdapter,
				index,
				entryIterator,
				ingestCallback,
				customFieldVisibilityWriter);
	}

	private <T> List<ByteArrayId> ingestInternal(
			final WritableDataAdapter<T> writableAdapter,
			final Index index,
			final Iterator<T> entryIterator,
			final IngestCallback<T> ingestCallback,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {
		this.adapterStore.addAdapter(writableAdapter);
		this.indexStore.addIndex(index);
		final List<ByteArrayId> ids = new ArrayList<ByteArrayId>();
		try (StatsCompositionTool<T> tool = new StatsCompositionTool<T>(
				writableAdapter,
				statsStore)) {
			final IndexWriter writer = createWriter(
					index,
					writableAdapter,
					new IngestCallback<T>() {

						@Override
						public void entryIngested(
								DataStoreEntryInfo entryInfo,
								T entry ) {
							((IngestCallback<T>) ingestCallback).entryIngested(
									entryInfo,
									entry);
							tool.entryIngested(
									entryInfo,
									entry);
						}
					},
					customFieldVisibilityWriter);
			while (entryIterator.hasNext()) {
				final T nextEntry = entryIterator.next();
				ids.addAll(writer.write(
						writableAdapter,
						nextEntry));
			}
		}
		catch (Exception e) {
			LOGGER.error(
					"Failed ingest",
					e);
		}
		return ids;
	}

	private <T> IndexWriter createWriter(
			final Index index,
			final WritableDataAdapter<T> writableAdapter,
			final IngestCallback<T> ingestCallback,
			final VisibilityWriter<T> customFieldVisibilityWriter ) {

		return new MyIndexWriter<T>(
				index,
				ingestCallback,
				customFieldVisibilityWriter);
	}

	private class MyIndexWriter<S> implements
			IndexWriter
	{
		final Index index;
		final IngestCallback<S> ingestCallback;
		final VisibilityWriter<S> customFieldVisibilityWriter;

		public MyIndexWriter(
				final Index index,
				final IngestCallback<S> ingestCallback,
				final VisibilityWriter<S> customFieldVisibilityWriter ) {
			super();
			this.index = index;
			this.ingestCallback = ingestCallback;
			this.customFieldVisibilityWriter = customFieldVisibilityWriter;
		}

		@Override
		public void close()
				throws IOException {}

		@Override
		public <T> List<ByteArrayId> write(
				final WritableDataAdapter<T> writableAdapter,
				final T entry ) {
			final List<ByteArrayId> ids = new ArrayList<ByteArrayId>();
			final List<EntryRow> rows = DataStoreUtils.entryToRows(
					writableAdapter,
					index,
					entry,
					(IngestCallback<T>) ingestCallback,
					(VisibilityWriter<T>) customFieldVisibilityWriter);
			for (final EntryRow row : rows) {
				ids.add(row.getRowId());
				getRowsForIndex(
						index.getId()).add(
						row);
			}
			return ids;
		}

		@Override
		public <T> void setupAdapter(
				final WritableDataAdapter<T> writableAdapter ) {}

		@Override
		public Index getIndex() {
			return index;
		}

	}

	@Override
	public CloseableIterator<?> query(
			final Query query ) {
		return query(
				query,
				-1);
	}

	private TreeSet<EntryRow> getRowsForIndex(
			ByteArrayId id ) {
		TreeSet<EntryRow> set = storeData.get(id);
		if (set == null) {
			set = new TreeSet<EntryRow>();
			storeData.put(
					id,
					set);
		}
		return set;
	}

	@Override
	public <T> T getEntry(
			final Index index,
			final ByteArrayId rowId ) {
		final Iterator<EntryRow> rowIt = getRowsForIndex(
				index.getId()).iterator();
		while (rowIt.hasNext()) {
			final EntryRow row = rowIt.next();
			if (Arrays.equals(
					row.getTableRowId().getRowId(),
					rowId.getBytes())) {
				return (T) row.getEntry();
			}
		}
		return null;
	}

	@Override
	public <T> T getEntry(
			final Index index,
			final ByteArrayId dataId,
			final ByteArrayId adapterId,
			final String... additionalAuthorizations ) {
		final Iterator<EntryRow> rowIt = getRowsForIndex(
				index.getId()).iterator();
		while (rowIt.hasNext()) {
			final EntryRow row = rowIt.next();
			if (Arrays.equals(
					row.getTableRowId().getDataId(),
					dataId.getBytes()) && Arrays.equals(
					row.getTableRowId().getAdapterId(),
					adapterId.getBytes()) && isAuthorized(
					row,
					additionalAuthorizations)) {
				return (T) row.getEntry();
			}
		}
		return null;
	}

	@Override
	public boolean deleteEntry(
			final Index index,
			final ByteArrayId dataId,
			final ByteArrayId adapterId,
			final String... authorizations ) {
		DataAdapter<?> adapter = adapterStore.getAdapter(adapterId);
		try (StatsCompositionTool<Object> tool = new StatsCompositionTool(
				adapter,
				statsStore)) {
			final Iterator<EntryRow> rowIt = getRowsForIndex(
					index.getId()).iterator();
			while (rowIt.hasNext()) {
				final EntryRow row = rowIt.next();
				if (Arrays.equals(
						row.getTableRowId().getDataId(),
						dataId.getBytes()) && Arrays.equals(
						row.getTableRowId().getAdapterId(),
						adapterId.getBytes()) && isAuthorized(
						row,
						authorizations)) {
					rowIt.remove();
					tool.entryDeleted(
							row.info,
							row.entry);
				}
			}
		}
		catch (Exception e) {
			LOGGER.error(
					"Failed deletetion",
					e);
		}
		return false;
	}

	@Override
	public <T> CloseableIterator<T> getEntriesByPrefix(
			final Index index,
			final ByteArrayId rowPrefix,
			final String... authorizations ) {

		final Iterator<EntryRow> rowIt = getRowsForIndex(
				index.getId()).iterator();

		return new CloseableIterator<T>() {

			EntryRow nextRow = null;

			private boolean getNext() {
				while ((nextRow == null) && rowIt.hasNext()) {
					final EntryRow row = rowIt.next();
					if (!Arrays.equals(
							rowPrefix.getBytes(),
							Arrays.copyOf(
									row.rowId.getRowId(),
									rowPrefix.getBytes().length))) {
						continue;
					}
					nextRow = row;
					break;
				}
				return nextRow != null;
			}

			@Override
			public boolean hasNext() {
				return getNext();
			}

			@Override
			public T next() {
				final EntryRow currentRow = nextRow;
				nextRow = null;
				if (currentRow == null) {
					throw new NoSuchElementException();
				}
				return (T) currentRow.entry;
			}

			@Override
			public void remove() {
				rowIt.remove();
			}

			@Override
			public void close()
					throws IOException {}
		};
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Query query ) {
		return (CloseableIterator<T>) query(
				Arrays.asList(adapter.getAdapterId()),
				query,
				-1);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final Index index,
			final Query query ) {
		return query(
				index,
				query,
				-1);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final Index index,
			final Query query,
			final QueryOptions queryOptions ) {
		return query(
				index,
				query,
				-1);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Index index,
			final Query query ) {
		return query(
				adapter,
				index,
				query,
				-1,
				new ScanCallback<T>() {

					@Override
					public void entryScanned(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				"");
	}

	@Override
	public CloseableIterator<?> query(
			final List<ByteArrayId> adapterIds,
			final Query query ) {
		return query(
				adapterIds,
				query,
				-1);
	}

	@Override
	public CloseableIterator<?> query(
			final Query query,
			final int limit ) {
		final List<ByteArrayId> adapterIds = new ArrayList<ByteArrayId>();
		try (CloseableIterator<DataAdapter<?>> adapterIt = adapterStore.getAdapters()) {
			while (adapterIt.hasNext()) {
				adapterIds.add(adapterIt.next().getAdapterId());
			}
		}
		catch (final IOException e) {
			LOGGER.error(
					"Failed to execute query " + query.toString(),
					e);
		}
		return query(
				adapterIds,
				query,
				limit);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Query query,
			final int limit ) {
		return (CloseableIterator<T>) query(
				Arrays.asList(adapter.getAdapterId()),
				query,
				limit);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final Index index,
			final Query query,
			final int limit ) {
		return query(
				null,
				index,
				query,
				limit,
				new ScanCallback<T>() {

					@Override
					public void entryScanned(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				"");
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Index index,
			final Query query,
			final int limit ) {
		return query(
				adapter,
				index,
				query,
				limit,
				new ScanCallback<T>() {

					@Override
					public void entryScanned(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				"");
	}

	@Override
	public CloseableIterator<?> query(
			final List<ByteArrayId> adapterIds,
			final Query query,
			final int limit ) {
		final CloseableIterator<Index> indexIt = indexStore.getIndices();
		return new CloseableIterator<Object>() {
			Iterator<ByteArrayId> adapterIt = adapterIds.iterator();
			Index index = null;
			int count = 0;
			CloseableIterator<Object> dataIt = null;

			private boolean getNext() {
				while ((dataIt == null) || !dataIt.hasNext()) {
					if (index == null) {
						if (indexIt.hasNext()) {
							index = indexIt.next();
						}
						else {
							return false;
						}
					}
					if ((adapterIt != null) && adapterIt.hasNext()) {
						dataIt = (CloseableIterator<Object>) query(
								adapterStore.getAdapter(adapterIt.next()),
								index,
								query,
								-1,
								new ScanCallback<Object>() {

									@Override
									public void entryScanned(
											final DataStoreEntryInfo entryInfo,
											final Object entry ) {

									}
								},
								"");
						continue;
					}
					index = null;
					adapterIt = adapterIds.iterator();
				}

				return true;

			}

			@Override
			public boolean hasNext() {
				return ((limit <= 0) || (count < limit)) && getNext();
			}

			@Override
			public Object next() {
				count++;
				return dataIt.next();
			}

			@Override
			public void remove() {
				dataIt.remove();

			}

			@Override
			public void close()
					throws IOException {
				indexIt.close();
			}

		};
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Index index,
			final Query query,
			final int limit,
			final String... authorizations ) {
		return query(
				adapter,
				index,
				query,
				limit,
				new ScanCallback<T>() {

					@Override
					public void entryScanned(
							final DataStoreEntryInfo entryInfo,
							final T entry ) {

					}
				},
				authorizations);
	}

	@Override
	public <T> CloseableIterator<T> query(
			final DataAdapter<T> adapter,
			final Index index,
			final Query query,
			final Integer limit,
			final ScanCallback<?> scanCallback,
			final String... authorizations ) {
		final Iterator<EntryRow> rowIt = query.isSupported(index) ? getRowsForIndex(
				index.getId()).iterator() : Collections.<EntryRow> emptyIterator();

		final List<QueryFilter> filters = query.createFilters(index.getIndexModel());
		return new CloseableIterator<T>() {
			int count = 0;
			EntryRow nextRow = null;

			private boolean getNext() {
				while ((nextRow == null) && rowIt.hasNext()) {
					final EntryRow row = rowIt.next();
					final DataAdapter<T> innerAdapter = (DataAdapter<T>) (adapter == null ? adapterStore.getAdapter(new ByteArrayId(
							row.getTableRowId().getAdapterId())) : adapter);
					final IndexedAdapterPersistenceEncoding encoding = DataStoreUtils.getEncoding(
							index.getIndexModel(),
							innerAdapter,
							row);
					boolean ok = true;
					for (final QueryFilter filter : filters) {
						if (!filter.accept(encoding)) {
							ok = false;
							break;
						}
					}
					if (ok) {
						count++;
						nextRow = row;
						break;
					}
				}
				return (nextRow != null) && ((limit == null) || (limit <= 0) || (count < limit));
			}

			@Override
			public boolean hasNext() {
				return getNext();
			}

			@Override
			public T next() {
				final EntryRow currentRow = nextRow;
				((ScanCallback<T>) scanCallback).entryScanned(
						currentRow.getInfo(),
						(T) currentRow.entry);
				nextRow = null;
				return (T) currentRow.entry;
			}

			@Override
			public void remove() {
				rowIt.remove();
			}

			@Override
			public void close()
					throws IOException {}
		};
	}

	private boolean isAuthorized(
			final EntryRow row,
			final String... authorizations ) {
		for (final FieldInfo info : row.info.getFieldInfo()) {
			if (!DataStoreUtils.isAuthorized(
					info.getVisibility(),
					authorizations)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void delete(
			Query query ) {
		try (CloseableIterator<?> it = this.query(query)) {
			while (it.hasNext()) {
				it.next();
				it.remove();
			}
		}
		catch (IOException e) {
			LOGGER.error(
					"Failed deletetion",
					e);
		}

	}

	public AdapterStore getAdapterStore() {
		return adapterStore;
	}

	public IndexStore getIndexStore() {
		return indexStore;
	}

	public DataStatisticsStore getStatsStore() {
		return statsStore;
	}
}
