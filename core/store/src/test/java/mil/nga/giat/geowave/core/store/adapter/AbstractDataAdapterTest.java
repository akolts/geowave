package mil.nga.giat.geowave.core.store.adapter;

import java.util.ArrayList;

import junit.framework.Assert;
import mil.nga.giat.geowave.core.index.PersistenceUtils;
import mil.nga.giat.geowave.core.store.adapter.MockComponents.MockAbstractDataAdapter;
import mil.nga.giat.geowave.core.store.adapter.MockComponents.TestNativeFieldHandler;
import mil.nga.giat.geowave.core.store.adapter.MockComponents.TestPersistentIndexFieldHandler;
import mil.nga.giat.geowave.core.store.index.CommonIndexValue;
import mil.nga.giat.geowave.core.store.index.Index;

import org.junit.Test;

public class AbstractDataAdapterTest
{

	public static void main(
			final String[] args ) {
		new AbstractDataAdapterTest().testAbstractDataAdapterPersistance();
	}

	@Test
	// *************************************************************************
	//
	// Test encode(..) and decode(..) methods of AbstractDataAdapter via
	// instantiation of MockAbstractDataAdapterTest.
	//
	// *************************************************************************
	public void testAbstractDataAdapterEncodeDecode() {
		// To instantiate MockAbstractDataAdapter, need to create
		// array of indexFieldHandlers and array of nativeFieldHandlers.
		final ArrayList<PersistentIndexFieldHandler<Integer, ? extends CommonIndexValue, Object>> indexFieldHandlers = new ArrayList<PersistentIndexFieldHandler<Integer, ? extends CommonIndexValue, Object>>();
		indexFieldHandlers.add(new MockComponents.TestPersistentIndexFieldHandler());

		final ArrayList<NativeFieldHandler<Integer, Object>> nativeFieldHandlers = new ArrayList<NativeFieldHandler<Integer, Object>>();
		nativeFieldHandlers.add(new MockComponents.TestNativeFieldHandler());

		final MockComponents.MockAbstractDataAdapter mockAbstractDataAdapter = new MockComponents.MockAbstractDataAdapter(
				indexFieldHandlers,
				nativeFieldHandlers);
		final MockComponents.TestIndexModel testIndexModel = new MockComponents.TestIndexModel();
		final Integer beforeValue = 123456;
		final AdapterPersistenceEncoding testEncoding = mockAbstractDataAdapter.encode(
				beforeValue,
				testIndexModel);
		final Integer afterValue = mockAbstractDataAdapter.decode(
				new IndexedAdapterPersistenceEncoding(
						testEncoding.getAdapterId(),
						testEncoding.getDataId(),
						null,
						1,
						testEncoding.getCommonData(),
						testEncoding.getAdapterExtendedData()),
				new Index(
						null,
						testIndexModel));

		Assert.assertEquals(
				"EncodeDecode_test",
				beforeValue,
				afterValue);
	}

	@Test
	public void testAbstractDataAdapterPersistance() {
		final ArrayList<PersistentIndexFieldHandler<Integer, ? extends CommonIndexValue, Object>> indexFieldHandlers = new ArrayList<PersistentIndexFieldHandler<Integer, ? extends CommonIndexValue, Object>>();
		indexFieldHandlers.add(new TestPersistentIndexFieldHandler());

		final ArrayList<NativeFieldHandler<Integer, Object>> nativeFieldHandlers = new ArrayList<NativeFieldHandler<Integer, Object>>();
		nativeFieldHandlers.add(new TestNativeFieldHandler());

		final MockAbstractDataAdapter mockAbstractDataAdapter = new MockAbstractDataAdapter(
				indexFieldHandlers,
				nativeFieldHandlers);

		final MockAbstractDataAdapter obj = PersistenceUtils.fromBinary(
				PersistenceUtils.toBinary(mockAbstractDataAdapter),
				MockAbstractDataAdapter.class);

		// TODO is there another test?
		Assert.assertNotNull(obj);
	}
}
