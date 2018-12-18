#!/bin/bash

rm -rf work intermediates output .nextflow* nextflow_logs

nextflow ribo.groovy -params-file sample.yml

sleep 1
