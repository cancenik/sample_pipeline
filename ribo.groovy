


// We need multiple copies of the input fastq channel
// to be used in some independent processes
Channel.from(params.raw_fastq.collect{k,v -> [k, file(v[0]) ] } 
	).into{ INPUT_LIBRARIES; INPUT_LIB_COPY; INPUT_LIBRARIES_EXIST;
          INPUT_LIBRARIES_MD5; INPUT_LIBRARIES_FASTQC;
          INPUT_LIBRARIES_MAP }

if(params.verbose){ 
   INPUT_LIB_COPY.subscribe{ println "$it" }
}

output_directory = params.output.output_directory


// Make sure that all input fastq files exist
// For any missing file, the pipelione will fail
INPUT_LIBRARIES_EXIST.subscribe{ lib, fastq_file ->
  existence_check:{thisfile = file(fastq_file)
   assert thisfile.exists()   
  }
}

// TODO
// Check the existnece of other files such as bowtie2 references etc.


file(output_directory).mkdir()



////////////////////////////////////////////////////////////////////
/// MD5 SUM ////////////////////////////////////////////////////////
if( params.do_md5sum){

md5sum_path = "${output_directory}/md5sum"

process md5sum{

   storeDir params.output.intermediates
   input:
   set val(library), file(fastq) from INPUT_LIBRARIES

   output:
   set val(library), file("${library}.md5") into MD5SUMS

   """
   md5sum $fastq > ${library}.md5
   """
}

//MD5SUMS.subscribe{ println it }



process merge_MD5{

	input:
	   set val(library), file(md5file) from MD5SUMS

	output:
	   set val(library), stdout into MD5LIST

	"""
	cat $md5file | awk \'{print(\$1)}\'
	"""

}

md5_all = file(md5sum_path)
md5_all.text = ""
MD5LIST.map{
             x, y -> [x.replaceAll("\\s",""), y.replaceAll("\\s","") ]
           }.subscribe{ a,b -> md5_all.append( "$b  $a\n") }

}
/// MD5 SUM ////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////

fastqc_directory = "${params.output.output_directory}/fastqc"
file(fastqc_directory).mkdir()

////////////////////////////////////////////////////////////////////
/// FastQC /////////////////////////////////////////////////////////
if( params.do_fastqc ){

process fastqc {

  publishDir fastqc_directory,  mode: 'copy'
  input:
     set val(library), file(fastq) from INPUT_LIBRARIES_FASTQC

  output:
     set val(library), file("${library}_fastqc.html"), 
                       file("${library}_fastqc.zip") into FASTQC_OUT

  """
  ln -s $fastq ${library}.fastq.gz

  fastqc ${library}.fastq.gz --outdir=\$PWD 

  """
}

}

/// FastQC /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////