#!/bin/bash

OWN_DIR='/groups/scicompsoft/home/ackermand/Programming/hot-knife' #`dirname "${BASH_SOURCE[0]}"`
ABS_DIR=`readlink -f "$OWN_DIR"`

FLINTSTONE=$OWN_DIR/flintstone/flintstone-lsd.sh
JAR=$OWN_DIR/target/hot-knife-0.0.4-SNAPSHOT.jar
CLASS=org.janelia.saalfeldlab.hotknife.SparkExpandMicrotubules
N_NODES=2

for i in {1,2,3,4}
do

path=/groups/cosem/cosem/ackermand/paperResultsWithFullPaths/evaluation/HeLa2/microtubuleEvaluation/validation/crop${i}.n5

ARGV="--inputN5Path '$path' \
--inputN5DatasetName 'microtubules_centerAxis' \
--innerRadiusInNm 6 \
--outerRadiusInNm 12.5 \
"

export RUNTIME="48:00"
TERMINATE=1 $FLINTSTONE $N_NODES $JAR $CLASS $ARGV 

ln -s $path/microtubules_centerAxis_expanded $path/microtubules

done
