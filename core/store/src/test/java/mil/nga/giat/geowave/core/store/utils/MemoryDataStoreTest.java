package mil.nga.giat.geowave.core.store.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import mil.nga.giat.geowave.core.index.NumericIndexStrategy;
import mil.nga.giat.geowave.core.index.sfc.data.BasicNumericDataset;
import mil.nga.giat.geowave.core.index.sfc.data.MultiDimensionalNumericData;
import mil.nga.giat.geowave.core.index.sfc.data.NumericData;
import mil.nga.giat.geowave.core.index.sfc.data.NumericRange;
import mil.nga.giat.geowave.core.store.CloseableIterator;
import mil.nga.giat.geowave.core.store.DataStoreEntryInfo;
import mil.nga.giat.geowave.core.store.IngestCallback;
import mil.nga.giat.geowave.core.store.adapter.MockComponents;
import mil.nga.giat.geowave.core.store.adapter.MockComponents.IntegerRangeDataStatistics;
import mil.nga.giat.geowave.core.store.adapter.MockComponents.TestIndexModel;
import mil.nga.giat.geowave.core.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.core.store.adapter.statistics.CountDataStatistics;
import mil.nga.giat.geowave.core.store.adapter.statistics.DataStatistics;
import mil.nga.giat.geowave.core.store.data.IndexedPersistenceEncoding;
import mil.nga.giat.geowave.core.store.filter.QueryFilter;
import mil.nga.giat.geowave.core.store.index.CommonIndexModel;
import mil.nga.giat.geowave.core.store.index.Index;
import mil.nga.giat.geowave.core.store.query.Query;

import org.junit.Test;

public class MemoryDataStoreTest
{

	@Test
	public void test()
			throws IOException {
		final Index index = new Index(
				new MockComponents.MockIndexStrategy(),
				new MockComponents.TestIndexModel());
		final WritableDataAdapter<Integer> adapter = new MockComponents.MockAbstractDataAdapter();
		final MemoryDataStore dataStore = new MemoryDataStore();
		final AtomicInteger record = new AtomicInteger(
				0);
		dataStore.ingest(
				adapter,
				index,
				Collections.singletonList(
						new Integer(
								25)).iterator(),
				new IngestCallback<Integer>() {

					@Override
					public void entryIngested(
							final DataStoreEntryInfo entryInfo,
							final Integer entry ) {
						record.set(entry.intValue());
					}
				});
		assertEquals(
				25,
				record.get());
		dataStore.ingest(
				adapter,
				index,
				new Integer(
						35));
		try (CloseableIterator<?> itemIt = dataStore.query(new TestQuery(
				23,
				26))) {
			assertTrue(itemIt.hasNext());
			assertEquals(
					new Integer(
							25),
					itemIt.next());
			assertFalse(itemIt.hasNext());
		}
		try (CloseableIterator<?> itemIt = dataStore.query(new TestQuery(
				23,
				36))) {
			assertTrue(itemIt.hasNext());
			assertEquals(
					new Integer(
							25),
					itemIt.next());
			assertTrue(itemIt.hasNext());
			assertEquals(
					new Integer(
							35),
					itemIt.next());
			assertFalse(itemIt.hasNext());
		}
		final Iterator<DataStatistics<?>> statsIt = dataStore.getStatsStore().getAllDataStatistics();
		assertTrue(checkStats(
				(DataStatistics<Integer>) statsIt.next(),
				2,
				new NumericRange(
						25,
						35)));
		assertTrue(checkStats(
				(DataStatistics<Integer>) statsIt.next(),
				2,
				new NumericRange(
						25,
						35)));
		assertFalse(statsIt.hasNext());

		dataStore.delete(new TestQuery(
				23,
				26));
		try (CloseableIterator<?> itemIt = dataStore.query(new TestQuery(
				23,
				36))) {
			assertTrue(itemIt.hasNext());
			assertEquals(
					new Integer(
							35),
					itemIt.next());
			assertFalse(itemIt.hasNext());
		}
		try (CloseableIterator<?> itemIt = dataStore.query(new TestQuery(
				23,
				26))) {
			assertFalse(itemIt.hasNext());
		}
		assertEquals(
				new Integer(
						35),
				dataStore.getEntry(
						index,
						adapter.getDataId(new Integer(
								35)),
						adapter.getAdapterId()));

	}

	private boolean checkStats(
			DataStatistics<Integer> stat,
			int count,
			NumericRange range ) {
		if (stat instanceof CountDataStatistics) {
			return ((CountDataStatistics) stat).getCount() == count;
		}
		else {
			return ((IntegerRangeDataStatistics) stat).getMin() == range.getMin() && ((IntegerRangeDataStatistics) stat).getMax() == range.getMax();
		}
	}

	private class TestQueryFilter implements
			QueryFilter
	{
		final CommonIndexModel indexModel;
		final double min, max;

		public TestQueryFilter(
				final CommonIndexModel indexModel,
				final double min,
				final double max ) {
			super();
			this.indexModel = indexModel;
			this.min = min;
			this.max = max;
		}

		@Override
		public boolean accept(
				final IndexedPersistenceEncoding persistenceEncoding ) {
			final double min = persistenceEncoding.getNumericData(
					indexModel.getDimensions()).getDataPerDimension()[0].getMin();
			final double max = persistenceEncoding.getNumericData(
					indexModel.getDimensions()).getDataPerDimension()[0].getMax();
			return !((this.max <= min) || (this.min > max));
		}

	}

	private class TestQuery implements
			Query
	{

		final double min, max;

		public TestQuery(
				final double min,
				final double max ) {
			super();
			this.min = min;
			this.max = max;
		}

		@Override
		public List<QueryFilter> createFilters(
				final CommonIndexModel indexModel ) {
			return Arrays.asList((QueryFilter) new TestQueryFilter(
					indexModel,
					min,
					max));
		}

		@Override
		public boolean isSupported(
				final Index index ) {
			return index.getIndexModel() instanceof TestIndexModel;
		}

		@Override
		public MultiDimensionalNumericData getIndexConstraints(
				final NumericIndexStrategy indexStrategy ) {
			return new BasicNumericDataset(
					new NumericData[] {
						new NumericRange(
								min,
								max)
					});
		}

	}
}
