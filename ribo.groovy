
// TODO:
// Channel namings should be more consistgent and systematic.
// Maybe we could do it based on process names?


// Checking the existence of index files is ugly.
// find a way to check them all in the same place.


////////////////////////////////////////////////////////////

// We need multiple copies of the input fastq channel
// to be used in some independent processes
Channel.from(params.raw_fastq.collect{k,v -> [k, file(v[0]) ] } 
	).into{ INPUT_LIBRARIES; INPUT_LIB_COPY; INPUT_LIBRARIES_EXIST;
          INPUT_LIBRARIES_MD5; INPUT_LIBRARIES_FASTQC;
          INPUT_LIBRARIES_FILTERING }

if(params.verbose){ 
   INPUT_LIB_COPY.subscribe{ println "$it" }
}

output_directory = params.output.output_directory
intermediates_directory = params.output.intermediates


// Make sure that all input fastq files exist
// For any missing file, the pipelione will fail
INPUT_LIBRARIES_EXIST.subscribe{ lib, fastq_file ->
  existence_check:{thisfile = file(fastq_file)
   assert thisfile.exists()   
  }
}

// TODO
// Check the existnece of other files such as bowtie2 references etc.


// TODO: Do we really want this???
file(output_directory).mkdir()


this_directory = (new File(params.reference.filter[0]).absolutePath)
//pieces = this_directory.split("/")

println "bowtie2 directory" +  this_directory



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
 
  executor 'local'
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

  fastqc ${library}.fastq.gz --outdir=\$PWD -t ${task.cpus}

  """
}

}

/// FastQC /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////
/// Filtering /////////////////////////////////////////////////////////

filtering_folder = "${intermediates_directory}/filtering"

bowtie2_filtering_reference = params.reference.filter[0]

// Let's check if at least one of the reference files exist.
// This catches most of the typos or existence problems
// in boowtie2 reference.
sample_filtering_referenmce_file = file("${bowtie2_filtering_reference}.1.bt2")

assert sample_filtering_referenmce_file.exists()
filtering_bowtie2_reference = (new File(bowtie2_filtering_reference).absolutePath)

process filtering {

  /*
  publishDir filtered_reads_folder, mode: 'move'
  publishDir kept_reads_folder, mode: 'move'
  */

  storeDir filtering_folder

  input:
     set val(library), file(fastq) from INPUT_LIBRARIES_FILTERING

  output:
     set val(library), file("${library}.filtering.al.fastq.gz") into FILTERING_AL_READS 
     set val(library), file("${library}.filtering.unal.fastq.gz") into FILTERING_UNAL_READS
     set val(library), file("${library}.filtering.bam") into FILTERING_BAM
     set val(library), file("${library}.filtering.log") into FILTERING_LOG

  """ 
     bowtie2 ${params.alignment_arguments.filtering} \
        -x ${filtering_bowtie2_reference} -q ${fastq} \
        --threads ${task.cpus} \
        --al-gz ${library}.filtering.al.fastq.gz \
        --un-gz ${library}.filtering.unal.fastq.gz  2>> ${library}.filtering.log \
        | samtools view -bS - > ${library}.filtering.bam
  """

}

/// Filtering /////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////
/// Transcriptome Allignment //////////////////////////////////////////

transcriptome_alignment_folder = "${intermediates_directory}/transcriptome_alignment"

bowtie2_transcriptome_alignment_reference = params.reference.transcriptome[0]
sample_transcriptome_alignment_file = file("${bowtie2_transcriptome_alignment_reference}.1.bt2")
assert sample_filtering_referenmce_file.exists()

transcriptome_alignment_bowtie2_reference = \
   (new File(bowtie2_transcriptome_alignment_reference).absolutePath)

process transcriptome_alignment{

  storeDir transcriptome_alignment_folder

  input:
     set val(library), file(filtering_unal_fastq) from FILTERING_UNAL_READS

  output:
     set val(library), file("${library}.transcriptome_alignment.unal.fastq.gz") into TRANSAL_UNAL_READS
     set val(library), file("${library}.transcriptome_alignment.bam") into TRANSAL_BAM
     set val(library), file("${library}.transcriptome_alignment.log") into TRANSAL_LOG

  """
  bowtie2 ${params.alignment_arguments.transcriptome} \
    --threads ${task.cpus} \
    -x $transcriptome_alignment_bowtie2_reference \
    -q $filtering_unal_fastq \
    --un ${library}.transcriptome_alignment.unal.fastq.gz \
    2>>${library}.transcriptome_alignment.log \
    | samtools view -bS - > ${library}.transcriptome_alignment.bam
  """

}

/// Transcriptome Allignment //////////////////////////////////////////
///////////////////////////////////////////////////////////////////////
