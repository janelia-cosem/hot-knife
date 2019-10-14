#!/bin/bash

OWN_DIR=`dirname "${BASH_SOURCE[0]}"`
ABS_DIR=`readlink -f "$OWN_DIR"`

FLINTSTONE=$ABS_DIR/flintstone/flintstone-lsd.sh
JAR=$PWD/target/hot-knife-0.0.4-SNAPSHOT.jar
CLASS=org.janelia.saalfeldlab.hotknife.SparkConnectedComponents
N_NODES=1

INPUTN5PATH='/nrs/saalfeld/heinrichl/cell/gt061719/unet/02-070219/hela_cell3_314000.n5'
INPUTN5GROUP='nucleolus'
#INPUTN5GROUP='nucleus,plasma_membrane,vesicle,vesicle_membrane'
OUTPUTN5PATH='/groups/cosem/cosem/ackermand/hela_cell3_314000_connected_components.n5'
#INPUTN5PATH='/groups/cosem/cosem/ackermand/hela_cell3_314000_crop.n5'
#OUTPUTN5PATH='/groups/cosem/cosem/ackermand/junk.n5'
MASKN5PATH='/groups/cosem/cosem/data/HeLa_Cell3_4x4x4nm/HeLa_Cell3_4x4x4nm.n5/'

ARGV="\
--inputN5Group '$INPUTN5GROUP' \
--inputN5Path '$INPUTN5PATH' \
--outputN5Path '$OUTPUTN5PATH' \
--maskN5Path '$MASKN5PATH'"

export RUNTIME="48:00"
TERMINATE=1 $FLINTSTONE $N_NODES $JAR $CLASS $ARGV