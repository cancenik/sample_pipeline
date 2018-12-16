#!/bin/bash

rm -rf work intermediates output .nextflow*

nextflow ribo.groovy -params-file ribo.yml

sleep 1
