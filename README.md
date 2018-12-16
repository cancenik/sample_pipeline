# sample_pipeline

## Running the Pipeline

A sample run of the pipeline is provided in the _run.sh_ script.

`bash run.sh`


## Dependencies
This pipeline is built using [Nextflow](https://www.nextflow.io/). We recommend installing it locally and including the folder of _nextflow_ executable in your *$PATH* environment variable.  

All other dependencies can be installed using conda. [Conda Installation instructions can be found in this link.](https://conda.io/docs/user-guide/install/index.html) Conda can be installed locally so you do NOT need admin priviliges. Once conda is installed, the dependencies can be installed via:

`conda env create -f environment.yml`

using the _environment.yml_ file provided in this repository.
