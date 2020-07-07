/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.hotknife;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;
import net.imglib2.algorithm.morphology.distance.DistanceTransform.DISTANCE_TYPE;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Connected components for an entire n5 volume
 *
 * @author David Ackerman &lt;ackermand@janelia.hhmi.org&gt;
 */
public class SparkContactSitesOld {
	@SuppressWarnings("serial")
	public static class Options extends AbstractOptions implements Serializable {

		@Option(name = "--inputN5Path", required = true, usage = "input N5 path, e.g. /path/to/segmented/data.n5")
		private String inputN5Path = null;

		@Option(name = "--outputN5Path", required = false, usage = "output N5 path, e.g. /path/to/output/data.n5")
		private String outputN5Path = null;

		@Option(name = "--inputN5DatasetName", required = false, usage = "N5 dataset, e.g. organelle")
		private String inputN5DatasetName = null;

		@Option(name = "--outputN5DatasetSuffix", required = false, usage = "N5 suffix, e.g. _cc so output would be organelle1_to_organelle2_cc")
		private String outputN5DatasetSuffix = "";

		@Option(name = "--contactDistance", required = false, usage = "Distance from orgnelle for contact site (nm)")
		private double contactDistance = 10;
		
		@Option(name = "--minimumVolumeCutoff", required = false, usage = "Minimum contact site cutoff (nm^3)")
		private double minimumVolumeCutoff = 35E3;
		
		@Option(name = "--doSelfContacts", required = false, usage = "Minimum contact site cutoff (nm^3)")
		private boolean doSelfContacts = false;

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);

				if (outputN5Path == null)
					outputN5Path = inputN5Path;

				parsedSuccessfully = true;
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		public String getInputN5Path() {
			return inputN5Path;
		}

		public String getInputN5DatasetName() {
			return inputN5DatasetName;
		}

		public String getOutputN5DatasetSuffix() {
			return outputN5DatasetSuffix;
		}

		public String getOutputN5Path() {
			return outputN5Path;
		}

		public double getContactDistance() {
			return contactDistance;
		}
		
		public double getMinimumVolumeCutoff() {
			return minimumVolumeCutoff;
		}
		
		public boolean getDoSelfContacts() {
			return doSelfContacts;
		}
	}
	
	/**
	 * Calculate contact boundaries around objects
	 *
	 * For a given segmented dataset, writes out an n5 file which contains halos around organelles within contactDistance. Each is labeled with the corresponding object ID from the segmentation.
	 *
	 * @param sc
	 * @param inputN5Path
	 * @param organelle
	 * @param outputN5Path
	 * @param outputN5DatasetName
	 * @param contactDistance
	 * @param blockInformationList
	 * @throws IOException
	 */
	public static final <T extends NativeType<T>> void calculateObjectContactBoundaries(
			final JavaSparkContext sc, final String inputN5Path, final String organelle,
			final String outputN5Path,
			final double contactDistance, final boolean doSelfContactSites, List<BlockInformation> blockInformationList) throws IOException {
		
			if(doSelfContactSites) calculateObjectContactBoundariesWithSelfContacts(sc, inputN5Path, organelle, outputN5Path, contactDistance, blockInformationList);	
			else calculateObjectContactBoundaries(sc, inputN5Path, organelle, outputN5Path, contactDistance, doSelfContactSites, blockInformationList);
	}
	
	public static final <T extends NativeType<T>> void calculateObjectContactBoundaries(
			final JavaSparkContext sc, final String inputN5Path, final String organelle,
			final String outputN5Path,
			final double contactDistance, List<BlockInformation> blockInformationList) throws IOException {
		// Get attributes of input data set
				final N5Reader n5Reader = new N5FSReader(inputN5Path);
				final DatasetAttributes attributes = n5Reader.getDatasetAttributes(organelle);
				final int[] blockSize = attributes.getBlockSize();
				final long[] outputDimensions = attributes.getDimensions();

				// Create output dataset
				String outputN5DatasetNameOrganelle = organelle+"_contact_boundary_temp_to_delete";
				final N5Writer n5Writer = new N5FSWriter(outputN5Path);
				n5Writer.createGroup(outputN5DatasetNameOrganelle);
				n5Writer.createDataset(outputN5DatasetNameOrganelle, outputDimensions, blockSize,
						org.janelia.saalfeldlab.n5.DataType.UINT64, attributes.getCompression());
				double [] pixelResolution = IOHelper.getResolution(n5Reader, organelle);
				n5Writer.setAttribute(outputN5DatasetNameOrganelle, "pixelResolution", new IOHelper.PixelResolution(pixelResolution));
				

				
				//Get contact distance in voxels
				final double contactDistanceInVoxels = contactDistance/pixelResolution[0];
				double contactDistanceInVoxelsSquared = contactDistanceInVoxels*contactDistanceInVoxels;//contactDistanceInVoxelsCeiling*contactDistanceInVoxelsCeiling;
				int contactDistanceInVoxelsCeiling=(int)Math.ceil(contactDistanceInVoxels);
				
				final JavaRDD<BlockInformation> rdd = sc.parallelize(blockInformationList);
				rdd.foreach(currentBlockInformation -> {
					// Get information for reading in/writing current block. Need to extend offset/dimension so that can accomodate objects from different blocks
					long[][] gridBlock = currentBlockInformation.gridBlock;
					long[] dimension = gridBlock[1];
					long[] extendedOffset = gridBlock[0].clone();
					long[] extendedDimension = gridBlock[1].clone();
					Arrays.setAll(extendedOffset, i->extendedOffset[i]-contactDistanceInVoxelsCeiling);
					Arrays.setAll(extendedDimension, i->extendedDimension[i]+2*contactDistanceInVoxelsCeiling);

					// Read in source block
					final N5Reader n5ReaderLocal = new N5FSReader(inputN5Path);			
					final RandomAccessibleInterval<UnsignedLongType> segmentedOrganelle = Views.offsetInterval(Views.extendZero(
									(RandomAccessibleInterval<UnsignedLongType>) N5Utils.open(n5ReaderLocal, organelle)),
							extendedOffset, extendedDimension);
			
					
					//Get distance transform
					final RandomAccessibleInterval<BoolType> objectsBinarized = Converters.convert(segmentedOrganelle,
							(a, b) -> b.set(a.getLong() > 0), new BoolType());
					ArrayImg<FloatType, FloatArray> distanceFromObjects = ArrayImgs.floats(extendedDimension);	
					DistanceTransform.binaryTransform(objectsBinarized, distanceFromObjects, DISTANCE_TYPE.EUCLIDIAN);
					
					// Access input/output 
					final RandomAccess<UnsignedLongType> segmentedOrganelleRandomAccess = segmentedOrganelle.randomAccess();		
					final Img<UnsignedLongType> extendedOutput =  new ArrayImgFactory<UnsignedLongType>(new UnsignedLongType()).create(extendedDimension);	

					final Cursor<UnsignedLongType> extendedOutputCursor = Views.flatIterable(extendedOutput).cursor();
					final Cursor<FloatType> distanceFromObjectsCursor = distanceFromObjects.cursor();
					// Loop over image and label voxel within halo according to object ID. Note: This will mean that each voxel only gets one object assignment
					while (extendedOutputCursor.hasNext()) {
						final UnsignedLongType voxelOutput = extendedOutputCursor.next();
						final float distanceFromObjectsSquared = distanceFromObjectsCursor.next().get();
						long[] position = {extendedOutputCursor.getLongPosition(0),  extendedOutputCursor.getLongPosition(1),  extendedOutputCursor.getLongPosition(2)};
						if( (distanceFromObjectsSquared>0 && distanceFromObjectsSquared<=contactDistanceInVoxelsSquared) || isSurfaceVoxel(segmentedOrganelleRandomAccess, position)) {//then voxel is within distance
							long objectID = findAndSetValueToNearestOrganelleID(voxelOutput, distanceFromObjectsSquared, position, segmentedOrganelleRandomAccess);
						}
					}
					
					RandomAccessibleInterval<UnsignedLongType> output = Views.offsetInterval(extendedOutput,new long[] {contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling},dimension);
					final N5Writer n5WriterLocal = new N5FSWriter(outputN5Path);
					N5Utils.saveBlock(output, n5WriterLocal, outputN5DatasetNameOrganelle, gridBlock[2]);
					
				});
	}
	
	@SuppressWarnings("unchecked")
	public static final <T extends NativeType<T>> void calculateObjectContactBoundariesWithSelfContacts(
			final JavaSparkContext sc, final String inputN5Path, final String organelle,
			final String outputN5Path,
			final double contactDistance, List<BlockInformation> blockInformationList) throws IOException {
				
		// Get attributes of input data set
		final N5Reader n5Reader = new N5FSReader(inputN5Path);
		final DatasetAttributes attributes = n5Reader.getDatasetAttributes(organelle);
		final int[] blockSize = attributes.getBlockSize();
		final long[] outputDimensions = attributes.getDimensions();

		// Create output dataset
		String outputN5DatasetNameOrganelle = organelle+"_contact_boundary_temp_to_delete";
		String outputN5DatasetNameOrganellePairs = organelle+"_pairs_contact_boundary_temp_to_delete";

		final N5Writer n5Writer = new N5FSWriter(outputN5Path);
		n5Writer.createGroup(outputN5DatasetNameOrganelle);
		n5Writer.createDataset(outputN5DatasetNameOrganelle, outputDimensions, blockSize,
				org.janelia.saalfeldlab.n5.DataType.UINT64, attributes.getCompression());
		double [] pixelResolution = IOHelper.getResolution(n5Reader, organelle);
		n5Writer.setAttribute(outputN5DatasetNameOrganelle, "pixelResolution", new IOHelper.PixelResolution(pixelResolution));
		
		//Pairs necessary for classA - classA contact sites
		n5Writer.createGroup(outputN5DatasetNameOrganellePairs);
		n5Writer.createDataset(outputN5DatasetNameOrganellePairs, outputDimensions, blockSize,
				org.janelia.saalfeldlab.n5.DataType.UINT64, attributes.getCompression());
		n5Writer.setAttribute(outputN5DatasetNameOrganellePairs, "pixelResolution", new IOHelper.PixelResolution(pixelResolution));
		
		//Get contact distance in voxels
		final double contactDistanceInVoxels = contactDistance/pixelResolution[0];
		double contactDistanceInVoxelsSquared = contactDistanceInVoxels*contactDistanceInVoxels;//contactDistanceInVoxelsCeiling*contactDistanceInVoxelsCeiling;
		int contactDistanceInVoxelsCeiling=(int)Math.ceil(contactDistanceInVoxels);
		
		final JavaRDD<BlockInformation> rdd = sc.parallelize(blockInformationList);
		rdd.foreach(currentBlockInformation -> {
			// Get information for reading in/writing current block. Need to extend offset/dimension so that can accomodate objects from different blocks
			long[][] gridBlock = currentBlockInformation.gridBlock;
			long[] dimension = gridBlock[1];
			long[] extendedOffset = gridBlock[0].clone();
			long[] extendedDimension = gridBlock[1].clone();
			Arrays.setAll(extendedOffset, i->extendedOffset[i]-contactDistanceInVoxelsCeiling);
			Arrays.setAll(extendedDimension, i->extendedDimension[i]+2*contactDistanceInVoxelsCeiling);

			// Read in source block
			final N5Reader n5ReaderLocal = new N5FSReader(inputN5Path);			
			final RandomAccessibleInterval<UnsignedLongType> segmentedOrganelle = Views.offsetInterval(Views.extendZero(
							(RandomAccessibleInterval<UnsignedLongType>) N5Utils.open(n5ReaderLocal, organelle)),
					extendedOffset, extendedDimension);
	
			
			//Get distance transform
			final RandomAccessibleInterval<BoolType> objectsBinarized = Converters.convert(segmentedOrganelle,
					(a, b) -> b.set(a.getLong() > 0), new BoolType());
			ArrayImg<FloatType, FloatArray> distanceFromObjects = ArrayImgs.floats(extendedDimension);	
			DistanceTransform.binaryTransform(objectsBinarized, distanceFromObjects, DISTANCE_TYPE.EUCLIDIAN);
			
			// Access input/output 
			final RandomAccess<UnsignedLongType> segmentedOrganelleRandomAccess = segmentedOrganelle.randomAccess();		
			final Img<UnsignedLongType> extendedOutput =  new ArrayImgFactory<UnsignedLongType>(new UnsignedLongType()).create(extendedDimension);	
			final Img<UnsignedLongType> extendedOutputPair =  new ArrayImgFactory<UnsignedLongType>(new UnsignedLongType()).create(extendedDimension); //if a voxel is within contactDistance of two organelles of the same type, that is a contact site	

			final Cursor<UnsignedLongType> extendedOutputCursor = Views.flatIterable(extendedOutput).cursor();
			final Cursor<UnsignedLongType> extendedOutputPairCursor = Views.flatIterable(extendedOutputPair).cursor();
			final Cursor<FloatType> distanceFromObjectsCursor = distanceFromObjects.cursor();
			// Loop over image and label voxel within halo according to object ID. Note: This will mean that each voxel only gets one object assignment
			while (extendedOutputCursor.hasNext()) {
				final UnsignedLongType voxelOutput = extendedOutputCursor.next();
				final UnsignedLongType voxelOutputPair = extendedOutputPairCursor.next();
				final float distanceFromObjectsSquared = distanceFromObjectsCursor.next().get();
				long[] position = {extendedOutputCursor.getLongPosition(0),  extendedOutputCursor.getLongPosition(1),  extendedOutputCursor.getLongPosition(2)};
				if( (distanceFromObjectsSquared>0 && distanceFromObjectsSquared<=contactDistanceInVoxelsSquared) || isSurfaceVoxel(segmentedOrganelleRandomAccess, position)) {//then voxel is within distance
					long objectID = findAndSetValueToNearestOrganelleID(voxelOutput, distanceFromObjectsSquared, position, segmentedOrganelleRandomAccess);
					if(objectID>0) {
						findAndSetValueToOrganellePairID(objectID, distanceFromObjectsSquared, voxelOutputPair, contactDistanceInVoxelsSquared, position, segmentedOrganelleRandomAccess);	
					}
				}
			}
			
			RandomAccessibleInterval<UnsignedLongType> output = Views.offsetInterval(extendedOutput,new long[] {contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling},dimension);
			RandomAccessibleInterval<UnsignedLongType> outputPair = Views.offsetInterval(extendedOutputPair,new long[] {contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling,contactDistanceInVoxelsCeiling},dimension);
			final N5Writer n5WriterLocal = new N5FSWriter(outputN5Path);
			N5Utils.saveBlock(output, n5WriterLocal, outputN5DatasetNameOrganelle, gridBlock[2]);
			N5Utils.saveBlock(outputPair, n5WriterLocal, outputN5DatasetNameOrganellePairs, gridBlock[2]);
			
		});
	}
	
	
	

	private static long findAndSetValueToNearestOrganelleID(UnsignedLongType voxelOutput,float distanceSquared, long [] position, RandomAccess<UnsignedLongType>segmentedOrganelleRandomAccess) {
		Set<List<Integer>> voxelsToCheck = getVoxelsToCheckBasedOnDistance(distanceSquared);
		for(List<Integer> voxelToCheck : voxelsToCheck) {
			int dx = voxelToCheck.get(0);
			int dy = voxelToCheck.get(1);
			int dz = voxelToCheck.get(2);
			segmentedOrganelleRandomAccess.setPosition(new long[] {position[0]+dx,position[1]+dy,position[2]+dz});
			long currentObjectID = segmentedOrganelleRandomAccess.get().get();
			if(currentObjectID > 0) {
				voxelOutput.set(currentObjectID);
				return currentObjectID;
			}	
		}
		return 0;
	}
	
	private static void findAndSetValueToOrganellePairID(long objectID, float distanceFromObjectSquared, UnsignedLongType voxelOutputPair, double contactDistanceInVoxelsSquared, long [] position, RandomAccess<UnsignedLongType> segmentedOrganelleRandomAccess){
		//For a given voxel outside an object, find the closest object to it and relabel the voxel with the corresponding object ID
		int distanceFromObject = (int) Math.floor(Math.sqrt(distanceFromObjectSquared));
		int distanceFromOrganellePairSquared = Integer.MAX_VALUE;
		long organellePairID=0;
		for(int radius = distanceFromObject; radius <= Math.sqrt(contactDistanceInVoxelsSquared); radius++) {//must be at least as far away as other organelle
			int radiusSquared = radius*radius;
			int radiusMinus1Squared = (radius-1)*(radius-1);
			for(int x=-radius; x<=radius; x++){
				for(int y=-radius; y<=radius; y++) {
					for(int z=-radius; z<=radius; z++) {
						int currentDistanceSquared = x*x+y*y+z*z;
						if(currentDistanceSquared>radiusMinus1Squared && currentDistanceSquared<=radiusSquared && currentDistanceSquared<=contactDistanceInVoxelsSquared) {
							segmentedOrganelleRandomAccess.setPosition(new long[] {position[0]+x,position[1]+y,position[2]+z});
							long currentObjectID = segmentedOrganelleRandomAccess.get().get();
							if(currentObjectID > 0 && currentObjectID != objectID) { //then is within contact distance of another organelle
								if(currentDistanceSquared < distanceFromOrganellePairSquared) { //ensure it is closest
									distanceFromOrganellePairSquared = currentDistanceSquared;
									organellePairID = currentObjectID;
								}
							
							}
						}
					}
				}
			}
			if (organellePairID>0) {
				voxelOutputPair.set(organellePairID);
				return;
			}
		}
		return;
	}

	
	
	/**
	 * Find connected components on a block-by-block basis and write out to
	 * temporary n5.
	 *
	 * Takes as input a threshold intensity, above which voxels are used for
	 * calculating connected components. Parallelization is done using a
	 * blockInformationList.
	 *
	 * @param sc
	 * @param inputN5Path
	 * @param inputN5DatasetName
	 * @param outputN5Path
	 * @param outputN5DatasetName
	 * @param maskN5PathName
	 * @param thresholdIntensity
	 * @param blockInformationList
	 * @throws IOException
	 */
	
	@SuppressWarnings("unchecked")
	public static final <T extends NativeType<T>> List<BlockInformation> blockwiseConnectedComponents(
			final JavaSparkContext sc, final String inputN5Path, String organelle1, String organelle2,
			final String outputN5Path, final String outputN5DatasetName, final double minimumVolumeCutoff,
			List<BlockInformation> blockInformationList) throws IOException {
		boolean sameOrganelleClassTemp=false;
		if(organelle1.equals(organelle2)) {
			organelle1+="_contact_boundary_temp_to_delete";
			organelle2+="_pairs_contact_boundary_temp_to_delete";
			sameOrganelleClassTemp=true;
		}
		else {
			organelle1+="_contact_boundary_temp_to_delete";
			organelle2+="_contact_boundary_temp_to_delete";
		}
		final boolean sameOrganelleClass = sameOrganelleClassTemp;
		final String organelle1ContactBoundaryString = organelle1;
		final String organelle2ContactBoundaryString = organelle2;
		// Get attributes of input data set
		final N5Reader n5Reader = new N5FSReader(inputN5Path);
		final DatasetAttributes attributes = n5Reader.getDatasetAttributes(organelle1);
		final int[] blockSize = attributes.getBlockSize();
		final long[] blockSizeL = new long[] { blockSize[0], blockSize[1], blockSize[2] };
		final long[] outputDimensions = attributes.getDimensions();
		final double [] pixelResolution = IOHelper.getResolution(n5Reader, organelle1);
		
		// Create output dataset
		final N5Writer n5Writer = new N5FSWriter(outputN5Path);
		n5Writer.createGroup(outputN5DatasetName);
		n5Writer.createDataset(outputN5DatasetName, outputDimensions, blockSize,
				org.janelia.saalfeldlab.n5.DataType.UINT64, attributes.getCompression());
		n5Writer.setAttribute(outputN5DatasetName, "pixelResolution", new IOHelper.PixelResolution(pixelResolution));

		// Set up rdd to parallelize over blockInformation list and run RDD, which will
		// return updated block information containing list of components on the edge of
		// the corresponding block
		final JavaRDD<BlockInformation> rdd = sc.parallelize(blockInformationList);
		JavaRDD<BlockInformation> javaRDDsets = rdd.map(currentBlockInformation -> {
			// Get information for reading in/writing current block
			long[][] gridBlock = currentBlockInformation.gridBlock;
			long[] offset = gridBlock[0];
			long[] dimension = gridBlock[1];

			// Read in source block
			final N5Reader n5ReaderLocal = new N5FSReader(inputN5Path);
			
			final RandomAccessibleInterval<UnsignedLongType>  organelle1Data = Views.offsetInterval(Views.extendZero(
						(RandomAccessibleInterval<UnsignedLongType>) N5Utils.open(n5ReaderLocal, organelle1ContactBoundaryString)
						),offset, dimension);
			final RandomAccessibleInterval<UnsignedLongType>  organelle2Data = Views.offsetInterval(Views.extendZero(
					(RandomAccessibleInterval<UnsignedLongType>) N5Utils.open(n5ReaderLocal, organelle2ContactBoundaryString)
					),offset, dimension);
			
			RandomAccess<UnsignedLongType> organelle1DataRA = organelle1Data.randomAccess();
			RandomAccess<UnsignedLongType> organelle2DataRA = organelle2Data.randomAccess();
			
			Set<List<Long>> contactSiteOrganellePairsSet = new HashSet<>();
			for(int x=0; x<dimension[0]; x++) {
				for(int y=0; y<dimension[1]; y++){
					for(int z=0; z<dimension[2]; z++) {
						int [] pos = new int[] {x,y,z};
						organelle1DataRA.setPosition(pos);
						organelle2DataRA.setPosition(pos);
						
						Long organelle1ID = organelle1DataRA.get().get();
						Long organelle2ID = organelle2DataRA.get().get();

						if(organelle1ID>0 && organelle2ID>0) { //then is part of a contact site
							contactSiteOrganellePairsSet.add(Arrays.asList(organelle1ID,organelle2ID));
						}
					}
				}
			}
			
			long[] currentDimensions = { 0, 0, 0 };
			organelle1Data.dimensions(currentDimensions);
			// Create the output based on the current dimensions
			
			final Img<UnsignedLongType> output = new ArrayImgFactory<UnsignedLongType>(new UnsignedLongType())
					.create(currentDimensions);
			
			final Img<UnsignedByteType> currentPairBinarized = new ArrayImgFactory<UnsignedByteType>(new UnsignedByteType()).create(currentDimensions);
			RandomAccess<UnsignedByteType> currentPairBinarizedRA = currentPairBinarized.randomAccess();
			currentBlockInformation.edgeComponentIDtoVolumeMap = new HashMap();
			currentBlockInformation.edgeComponentIDtoOrganelleIDs = new HashMap();
			for( List<Long> organellePairs : contactSiteOrganellePairsSet ) {
				for(int x=0; x<dimension[0]; x++) {
					for(int y=0; y<dimension[1]; y++){
						for(int z=0; z<dimension[2]; z++) {
							int [] pos = new int[] {x,y,z};
							organelle1DataRA.setPosition(pos);
							organelle2DataRA.setPosition(pos);
							currentPairBinarizedRA.setPosition(pos);
							Long organelle1ID = organelle1DataRA.get().get();
							Long organelle2ID = organelle2DataRA.get().get();
							if(organelle1ID.equals(organellePairs.get(0)) && organelle2ID.equals(organellePairs.get(1))) {
								currentPairBinarizedRA.get().set(1);
							}
							else if(sameOrganelleClass && (organelle1ID.equals(organellePairs.get(1)) && organelle2ID.equals(organellePairs.get(0))) ) {//if it is the same organelle class, then ids can be swapped since they still correspond to the same pair
								currentPairBinarizedRA.get().set(1);
							}
							else {
								currentPairBinarizedRA.get().set(0);
							}
						}
					}
				}
				// Compute the connected components which returns the components along the block
				// edges, and update the corresponding blockInformation object
				int minimumVolumeCutoffInVoxels = (int) Math.ceil(minimumVolumeCutoff/Math.pow(pixelResolution[0],3));
				
				Map<Long, Long> currentPairEdgeComponentIDtoVolumeMap = SparkConnectedComponents.computeConnectedComponents(currentPairBinarized, output, outputDimensions,
						blockSizeL, offset, 1, minimumVolumeCutoffInVoxels);
				currentBlockInformation.edgeComponentIDtoVolumeMap.putAll(currentPairEdgeComponentIDtoVolumeMap);
				for(Long edgeComponentID : currentPairEdgeComponentIDtoVolumeMap.keySet()) {
					if(sameOrganelleClass) {
						Long o1 = organellePairs.get(0);
						Long o2 = organellePairs.get(1);
						List<Long> sortedPair = o1<o2 ? Arrays.asList(o1,o2) : Arrays.asList(o2,o1);
						currentBlockInformation.edgeComponentIDtoOrganelleIDs.put(edgeComponentID, sortedPair);
					}
					else{
						currentBlockInformation.edgeComponentIDtoOrganelleIDs.put(edgeComponentID, Arrays.asList(organellePairs.get(0), organellePairs.get(1)));
					}
				}
				
			}

			// Write out output to temporary n5 stack
			final N5Writer n5WriterLocal = new N5FSWriter(outputN5Path);
			N5Utils.saveBlock(output, n5WriterLocal, outputN5DatasetName, gridBlock[2]);

			return currentBlockInformation;
		});

		// Run, collect and return blockInformationList
		blockInformationList = javaRDDsets.collect();

		return blockInformationList;
	}
	
	
	/**
	 * Merge all necessary objects obtained from blockwise connected components.
	 *
	 * Determines which objects need to be fused based on which ones touch at the
	 * boundary between blocks. Then performs the corresponding union find so each
	 * complete object has a unique id. Parallelizes over block information list.
	 *
	 * @param sc
	 * @param inputN5Path
	 * @param inputN5DatasetName
	 * @param blockInformationList
	 * @throws IOException
	 */
	public static final <T extends NativeType<T>> List<BlockInformation> unionFindConnectedComponents(
			final JavaSparkContext sc, final String inputN5Path, final String inputN5DatasetName, double minimumVolumeCutoff, final HashMap<Long,List<Long>> edgeComponentIDtoOrganelleIDs,
			List<BlockInformation> blockInformationList) throws IOException {

		// Get attributes of input data set:
		final N5Reader n5Reader = new N5FSReader(inputN5Path);
		final DatasetAttributes attributes = n5Reader.getDatasetAttributes(inputN5DatasetName);
		final int[] blockSize = attributes.getBlockSize();
		final double [] pixelResolution = IOHelper.getResolution(n5Reader, inputN5DatasetName);

		// Set up and run RDD, which will return the set of pairs of object IDs that
		// need to be fused
		final JavaRDD<BlockInformation> rdd = sc.parallelize(blockInformationList);
		JavaRDD<Set<List<Long>>> javaRDDsets = rdd.map(currentBlockInformation -> {
			// Get information for reading in/writing current block
			long[][] gridBlock = currentBlockInformation.gridBlock;
			long[] offset = gridBlock[0];
			long[] dimension = gridBlock[1];

			// Get source
			final N5Reader n5ReaderLocal = new N5FSReader(inputN5Path);
			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<UnsignedLongType> source = (RandomAccessibleInterval<UnsignedLongType>)N5Utils.open(n5ReaderLocal, inputN5DatasetName);
			long[] sourceDimensions = { 0, 0, 0 };
			source.dimensions(sourceDimensions);

			// Get hyperplanes of current block (x-most edge, y-most edge, z-most edge), and
			// the corresponding hyperplanes for the x+1 block, y+1 block and z+1 block.
			RandomAccessibleInterval<UnsignedLongType> xPlane1, yPlane1, zPlane1, xPlane2, yPlane2, zPlane2;
			xPlane1 = yPlane1 = zPlane1 = xPlane2 = yPlane2 = zPlane2 = null;

			long xOffset = offset[0] + blockSize[0];
			long yOffset = offset[1] + blockSize[1];
			long zOffset = offset[2] + blockSize[2];
			xPlane1 = Views.offsetInterval(Views.extendZero(source), new long[] { xOffset - 1, offset[1], offset[2] },
					new long[] { 1, dimension[1], dimension[2] });
			yPlane1 = Views.offsetInterval(Views.extendZero(source), new long[] { offset[0], yOffset - 1, offset[2] },
					new long[] { dimension[0], 1, dimension[2] });
			zPlane1 = Views.offsetInterval(Views.extendZero(source), new long[] { offset[0], offset[1], zOffset - 1 },
					new long[] { dimension[0], dimension[1], 1 });

			if (xOffset < sourceDimensions[0])
				xPlane2 = Views.offsetInterval(Views.extendZero(source), new long[] { xOffset, offset[1], offset[2] },
						new long[] { 1, dimension[1], dimension[2] });
			if (yOffset < sourceDimensions[1])
				yPlane2 = Views.offsetInterval(Views.extendZero(source), new long[] { offset[0], yOffset, offset[2] },
						new long[] { dimension[0], 1, dimension[2] });
			if (zOffset < sourceDimensions[2])
				zPlane2 = Views.offsetInterval(Views.extendZero(source), new long[] { offset[0], offset[1], zOffset },
						new long[] { dimension[0], dimension[1], 1 });

			// Calculate the set of object IDs that are touching and need to be merged
			Set<List<Long>> globalIDtoGlobalIDSet = new HashSet<>();
			getGlobalIDsToMerge(xPlane1, xPlane2, edgeComponentIDtoOrganelleIDs, globalIDtoGlobalIDSet);
			getGlobalIDsToMerge(yPlane1, yPlane2, edgeComponentIDtoOrganelleIDs, globalIDtoGlobalIDSet);
			getGlobalIDsToMerge(zPlane1, zPlane2, edgeComponentIDtoOrganelleIDs, globalIDtoGlobalIDSet);

			return globalIDtoGlobalIDSet;
		});

		// Collect and combine the sets of objects to merge
		long t0 = System.currentTimeMillis();
		Set<List<Long>> globalIDtoGlobalIDFinalSet = javaRDDsets.reduce((a,b) -> {a.addAll(b); return a; });
		long t1 = System.currentTimeMillis();
		System.out.println(globalIDtoGlobalIDFinalSet.size());
		System.out.println("Total unions = " + globalIDtoGlobalIDFinalSet.size());

		// Perform union find to merge all touching objects
		UnionFindDGA unionFind = new UnionFindDGA(globalIDtoGlobalIDFinalSet);
		unionFind.getFinalRoots();

		long t2 = System.currentTimeMillis();

		System.out.println("collect time: " + (t1 - t0));
		System.out.println("union find time: " + (t2 - t1));
		System.out.println("Total edge objects: " + unionFind.globalIDtoRootID.values().stream().distinct().count());

		// Add block-specific relabel map to the corresponding block information object
		Map<Long, Long> rootIDtoVolumeMap= new HashMap<Long, Long>();
		for (BlockInformation currentBlockInformation : blockInformationList) {
			Map<Long, Long> currentGlobalIDtoRootIDMap = new HashMap<Long, Long>();
			for (Long currentEdgeComponentID : currentBlockInformation.edgeComponentIDtoVolumeMap.keySet()) {
				Long rootID;
				rootID = unionFind.globalIDtoRootID.getOrDefault(currentEdgeComponentID, currentEdgeComponentID); // Need this check since not all edge objects will be connected to neighboring blocks
				currentGlobalIDtoRootIDMap.put(currentEdgeComponentID, rootID);
				rootIDtoVolumeMap.put(rootID, rootIDtoVolumeMap.getOrDefault(rootID, 0L) + currentBlockInformation.edgeComponentIDtoVolumeMap.get(currentEdgeComponentID));
				
			}
			currentBlockInformation.edgeComponentIDtoRootIDmap = currentGlobalIDtoRootIDMap;
		}
		
		int minimumVolumeCutoffInVoxels = (int) Math.ceil(minimumVolumeCutoff/Math.pow(pixelResolution[0],3));
		for (BlockInformation currentBlockInformation : blockInformationList) {
			for (Entry <Long,Long> e : currentBlockInformation.edgeComponentIDtoRootIDmap.entrySet()) {
				Long key = e.getKey();
				Long value = e.getValue();
				currentBlockInformation.edgeComponentIDtoRootIDmap.put(key, 
						rootIDtoVolumeMap.get(value) <= minimumVolumeCutoffInVoxels ? 0L : value);
			}
		}
		
		

		return blockInformationList;
	}
	
	
	public static final void getGlobalIDsToMerge(RandomAccessibleInterval<UnsignedLongType> hyperSlice1,
			RandomAccessibleInterval<UnsignedLongType> hyperSlice2, final HashMap<Long,List<Long>> edgeComponentIDtoOrganelleIDs, Set<List<Long>> globalIDtoGlobalIDSet) {
		// The global IDS that need to be merged are those that are touching along the
		// hyperplane borders between adjacent blocks
		if (hyperSlice1 != null && hyperSlice2 != null) {
			Cursor<UnsignedLongType> hs1Cursor = Views.flatIterable(hyperSlice1).cursor();
			Cursor<UnsignedLongType> hs2Cursor = Views.flatIterable(hyperSlice2).cursor();
			while (hs1Cursor.hasNext()) {
				long hs1Value = hs1Cursor.next().getLong();
				long hs2Value = hs2Cursor.next().getLong();
				
				List<Long> organelleIDs1 = edgeComponentIDtoOrganelleIDs.get(hs1Value);
				List<Long> organelleIDs2 = edgeComponentIDtoOrganelleIDs.get(hs2Value);
				if (hs1Value > 0 && hs2Value > 0 && organelleIDs1.equals(organelleIDs2)) {
					globalIDtoGlobalIDSet.add(Arrays.asList(hs1Value, hs2Value));// hs1->hs2 pair should always be
																					// distinct since hs1 is unique to
																					// first block
				}
			}

		}
	}
	
	public static final Set<List<Integer>> getVoxelsToCheckBasedOnDistance(float distanceSquared) {
		Set<List<Integer>> voxelsToCheck= new HashSet<>();
		double distance = Math.sqrt(distanceSquared);
		int [] posNeg = new int [] {1,-1};
		for(int i=0; i<=distance;i++) {
			for(int j=0; j<=distance; j++) {
				for(int k=0; k<=distance; k++) {
					if (i*i+j*j+k*k == distanceSquared) {
						for(int ip : posNeg) {
							for(int jp : posNeg) {
								for(int kp : posNeg) {
									voxelsToCheck.add(Arrays.asList(i*ip,j*jp,k*kp));
								}
							}
						}
					}
				}
			}
		}	
		return voxelsToCheck;
	}
	
	
	public static final void calculateContactSites(final String [] organelles, final SparkConf conf,final double minimumVolumeCutoff, final String inputN5Path, final String outputN5Path ) throws IOException {
		List<String> directoriesToDelete = new ArrayList<String>();
		for (int i = 0; i<organelles.length; i++) {
			final String organelle1 =organelles[i];
			for(int j= i; j< organelles.length; j++) {
				String organelle2 = organelles[j];
				List<BlockInformation> blockInformationList = BlockInformation.buildBlockInformationList(inputN5Path, organelle1);
				JavaSparkContext sc = new JavaSparkContext(conf);
				
				final String organelleContactString = organelle1 + "_to_" + organelle2;
				final String tempOutputN5ConnectedComponents = organelleContactString + "_cc_blockwise_temp_to_delete";
				final String finalOutputN5DatasetName = organelleContactString + "_cc";
				
				blockInformationList = blockwiseConnectedComponents(
						sc, outputN5Path,
						organelle1, organelle2, 
						outputN5Path,
						tempOutputN5ConnectedComponents,
						minimumVolumeCutoff,
						blockInformationList);
				
				HashMap<Long, List<Long>> edgeComponentIDtoOrganelleIDs = new HashMap<Long,List<Long>>();
				for(BlockInformation currentBlockInformation : blockInformationList) {
					edgeComponentIDtoOrganelleIDs.putAll(currentBlockInformation.edgeComponentIDtoOrganelleIDs);
				}
				blockInformationList = unionFindConnectedComponents(sc, outputN5Path, tempOutputN5ConnectedComponents, minimumVolumeCutoff,edgeComponentIDtoOrganelleIDs, blockInformationList);
				
				SparkConnectedComponents.mergeConnectedComponents(sc, outputN5Path, tempOutputN5ConnectedComponents, finalOutputN5DatasetName, blockInformationList);				
				directoriesToDelete.add(outputN5Path + "/" + tempOutputN5ConnectedComponents);
				sc.close();
			}
		}
		
		SparkDirectoryDelete.deleteDirectories(conf, directoriesToDelete);
	}
	
	public static boolean isSurfaceVoxel(final RandomAccess<UnsignedLongType> sourceRandomAccess, long [] position ) {
		sourceRandomAccess.setPosition(position);
		long referenceVoxelValue = sourceRandomAccess.get().get();
		if(referenceVoxelValue==0) {//needs to be inside organelle
			return false;
		}
		else{
			for(int dx=-1; dx<=1; dx++) {
				for(int dy=-1; dy<=1; dy++) {
					for(int dz=-1; dz<=1; dz++) {
						if(!(dx==0 && dy==0 && dz==0)) {
							final long testPosition[] = {position[0]+dx, position[1]+dy, position[2]+dz};
							sourceRandomAccess.setPosition(testPosition);
							if(sourceRandomAccess.get().get() != referenceVoxelValue) {
								return true;
							}
						}
					}
				}
			}
	
			return false;	
		}
	
	}
	
	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {
		final Options options = new Options(args);
		if (!options.parsedSuccessfully)
			return;
		final SparkConf conf = new SparkConf().setAppName("SparkContactSites");

		// Get all organelles
		String[] organelles = { "" };
		if (options.getInputN5DatasetName() != null) {
			organelles = options.getInputN5DatasetName().split(",");
		} else {
			File file = new File(options.getInputN5Path());
			organelles = file.list(new FilenameFilter() {
				@Override
				public boolean accept(File current, String name) {
					return new File(current, name).isDirectory();
				}
			});
		}
		
		//First calculate the object contact boundaries
		for (String organelle : organelles) {
			JavaSparkContext sc = new JavaSparkContext(conf);
			List<BlockInformation> blockInformationList = BlockInformation.buildBlockInformationList(options.getInputN5Path(), organelle);
			calculateObjectContactBoundaries(sc, options.getInputN5Path(), organelle,
					options.getOutputN5Path(),
					options.getContactDistance(), options.getDoSelfContacts(), blockInformationList);
			sc.close();
		}
		
		//For each pair of object classes, calculate the contact sites and get the connected component information
		/*List<String> directoriesToDelete = new ArrayList<String>();
		for (int i = 0; i<organelles.length; i++) {
			final String organelle1 =organelles[i];
			for(int j= i; j< organelles.length; j++) {
				String organelle2 = organelles[j];
				List<BlockInformation> blockInformationList = BlockInformation.buildBlockInformationList(options.getInputN5Path(), organelle1);
				JavaSparkContext sc = new JavaSparkContext(conf);
				
				final String organelleContactString = organelle1 + "_to_" + organelle2;
				final String tempOutputN5ConnectedComponents = organelleContactString + "_cc_blockwise_temp_to_delete";
				final String finalOutputN5DatasetName = organelleContactString + "_cc";
				
				double minimumVolumeCutoff = options.getMinimumVolumeCutoff();
				blockInformationList = blockwiseConnectedComponents(
						sc, options.getOutputN5Path(),
						organelle1, organelle2, 
						options.getOutputN5Path(),
						tempOutputN5ConnectedComponents,
						minimumVolumeCutoff,
						blockInformationList);
				
				HashMap<Long, List<Long>> edgeComponentIDtoOrganelleIDs = new HashMap<Long,List<Long>>();
				for(BlockInformation currentBlockInformation : blockInformationList) {
					edgeComponentIDtoOrganelleIDs.putAll(currentBlockInformation.edgeComponentIDtoOrganelleIDs);
				}
				blockInformationList = unionFindConnectedComponents(sc, options.getOutputN5Path(), tempOutputN5ConnectedComponents, minimumVolumeCutoff,edgeComponentIDtoOrganelleIDs, blockInformationList);
				
				SparkConnectedComponents.mergeConnectedComponents(sc, options.getOutputN5Path(), tempOutputN5ConnectedComponents, finalOutputN5DatasetName, blockInformationList);				
				directoriesToDelete.add(options.getOutputN5Path() + "/" + tempOutputN5ConnectedComponents);
				sc.close();
			}
		}
		
		SparkDirectoryDelete.deleteDirectories(conf, directoriesToDelete);
		System.out.println("Stage 5 Complete: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));*/
		calculateContactSites(organelles, conf, options.getMinimumVolumeCutoff(), options.getInputN5Path(), options.getOutputN5Path());
		
	}
}