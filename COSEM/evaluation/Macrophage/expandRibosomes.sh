#!/bin/bash

OWN_DIR='/groups/scicompsoft/home/ackermand/Programming/hot-knife' #`dirname "${BASH_SOURCE[0]}"`
ABS_DIR=`readlink -f "$OWN_DIR"`

FLINTSTONE=$OWN_DIR/flintstone/flintstone-lsd.sh
JAR=$OWN_DIR/target/hot-knife-0.0.4-SNAPSHOT.jar
CLASS=org.janelia.saalfeldlab.hotknife.SparkExpandDataset
N_NODES=2

BASEPATH=/groups/cosem/cosem/ackermand/paperResultsWithFullPaths/evaluation/Macrophage/

INPUTN5PATH=$BASEPATH/trainingCC.n5
ARGV="--inputN5DatasetName 'ribosomes_centers' \
--inputN5Path '$INPUTN5PATH' \
--expansionInNm 10 \
--outputN5DatasetSuffix '_expansion_10'"

TERMINATE=1 $FLINTSTONE $N_NODES $JAR $CLASS $ARGV
ln -s $INPUTN5PATH/ribosomes_centers_expansion_10 $INPUTN5PATH/ribosomes


INPUTN5PATH=$BASEPATH/refinedPredictionsCC.n5
ARGV="--inputN5DatasetName 'ribosomes_centers' \
--inputN5Path '$INPUTN5PATH' \
--expansionInNm 10 \
--outputN5DatasetSuffix '_expansion_10'"

TERMINATE=1 $FLINTSTONE $N_NODES $JAR $CLASS $ARGV
ln -s $INPUTN5PATH/ribosomes_centers_expansion_10 $INPUTN5PATH/ribosomes

