package mil.nga.giat.geowave.datastore.hbase.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mil.nga.giat.geowave.core.index.Mergeable;
import mil.nga.giat.geowave.core.index.PersistenceUtils;
import mil.nga.giat.geowave.core.store.query.aggregate.Aggregation;
import mil.nga.giat.geowave.datastore.hbase.query.generated.AggregationProtos;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.coprocessor.CoprocessorException;
import org.apache.hadoop.hbase.coprocessor.CoprocessorService;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.protobuf.ResponseConverter;
import org.apache.hadoop.hbase.regionserver.Region;
import org.apache.hadoop.hbase.regionserver.RegionScanner;

import com.google.protobuf.ByteString;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.Service;

public class AggregationEndpoint extends
		AggregationProtos.AggregationService implements
		Coprocessor,
		CoprocessorService
{

	private RegionCoprocessorEnvironment env;

	@Override
	public void start(
			CoprocessorEnvironment env )
			throws IOException {
		if (env instanceof RegionCoprocessorEnvironment) {
			this.env = (RegionCoprocessorEnvironment) env;
		}
		else {
			throw new CoprocessorException(
					"Must be loaded on a table region!");
		}
	}

	@Override
	public void stop(
			CoprocessorEnvironment env )
			throws IOException {
		// nothing to do when coprocessor is shutting down
	}

	@Override
	public Service getService() {
		return this;
	}

	@Override
	public void aggregate(
			RpcController controller,
			AggregationProtos.AggregationRequest request,
			RpcCallback<AggregationProtos.AggregationResponse> done ) {
		FilterList filterList = null;

		AggregationProtos.AggregationResponse response = null;
		ByteString value = ByteString.EMPTY;

		System.out.println("Calling aggregate on coprocessor ");

		// Get the aggregation type
		String aggregationType = request.getType().getName();
		Aggregation aggregation = null;

		try {
			aggregation = (Aggregation) Class.forName(
					aggregationType).newInstance();

			// TODO: Handle aggregation params
		}
		catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}

		if (aggregation != null) {
			try {
				if (request.getFilter() != null && !request.getFilter().isEmpty()) {
					HBaseDistributableFilter hdFilter = new HBaseDistributableFilter();
					
					ByteString filterByteString = request.getFilter();
					System.out.println("Dist filter content = " + filterByteString.toStringUtf8());

					byte[] filterBytes = filterByteString.toByteArray();
					System.out.println("Dist filter size = " + filterBytes.length);
					
					byte[] modelBytes = request.getModel().toByteArray();

					if (hdFilter.init(
							filterBytes,
							modelBytes)) {
						filterList = new FilterList(
								hdFilter);

						System.out.println("Created distributable filter... ");
					}
					else {
						System.out.println("Error creating distributable filter. ");
					}
				}
				else {
					System.out.println("Input distributable filter is undefined. ");
				}
			}
			catch (Exception e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}

			if (request.getRangefilter() != null) {
				byte[] rfilterBytes = request.getRangefilter().toByteArray();

				try {
					MultiRowRangeFilter rangeFilter = MultiRowRangeFilter.parseFrom(rfilterBytes);

					if (filterList == null) {
						filterList = new FilterList(
								rangeFilter);
					}
					else {
						filterList.addFilter(rangeFilter);
					}

					System.out.println("Created range filter... ");
				}
				catch (Exception e) {
					System.err.println(e.getMessage());
					e.printStackTrace();
				}
			}
			else {
				System.out.println("Input range filter is undefined. ");
			}

			System.out.println("Scanning... ");
			try {
				Mergeable mvalue = getValue(
						aggregation,
						filterList);

				byte[] bvalue = PersistenceUtils.toBinary(mvalue);
				value = ByteString.copyFrom(bvalue);

				System.out.println("Done scanning. Value = (" + value + ") for region ");
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
				System.err.println(ioe.getMessage());

				ResponseConverter.setControllerException(
						controller,
						ioe);
			}
			catch (Exception e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
		}

		System.out.println("Setting response... ");

		response = AggregationProtos.AggregationResponse.newBuilder().setValue(
				value).build();

		System.out.println("Coprocessor finished. ");

		done.run(response);
	}

	private Mergeable getValue(
			Aggregation aggregation,
			Filter filter )
			throws IOException {
		Scan scan = new Scan();
		scan.setMaxVersions(1);

		if (filter != null) {
			scan.setFilter(filter);
		}

		aggregation.clearResult();

		Region region = env.getRegion();

		RegionScanner scanner = region.getScanner(scan);
		region.startRegionOperation();

		// TODO: This only works for 'count' aggregation.
		// Anything else will need native -> common conversion
		List<Cell> results = new ArrayList<Cell>();
		while (scanner.nextRaw(results)) {			
			aggregation.aggregate(results);
		}

		scanner.close();
		region.closeRegionOperation();

		return aggregation.getResult();
	}
}
