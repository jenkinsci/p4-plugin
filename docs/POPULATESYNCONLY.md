# Sync Only 
This option is used if you have a hand-crafted build environment that you don't want to lose any files from. No cleanup is attempted and the sync will update all files (as **CLOBBER** is set) to the required revisions. This option is best for incremental builds and is mostly used for Pipeline builds. 
![Auto Cleanup and Sync](docs/images/populatesynconly.png)

## Options:
You can select a number of options: 
- **Force sync:** Replaces the workspace files. 
- **Populate have list:**  Overwrites the Perforce "have list". 
- **Sync with MODTIME for consistency check:** Preserves the modification time of files during the sync operation.
- **QUIET Perforce messages:** Enables the -q flag for all applicable Perforce Helix Core Server operations. Summary details will still be displayed.
- **Revert any open or unshelved files:** Used to undo any changes made by previous builds based on Helix Swarm reviews or changes made manually within Perforce. 
- **Pin build at Perforce Label:** When a build is triggered by **Polling**, **Build Now** or an external action, the workspace will only sync to the Perforce label in this field. Any other specified change or label will be ignored.
Supports variable expansion, for example `${VAR}`. If *now* is used, or a variable that expands to *now*, the latest change is used (within the scope of the workspace view). For more information about environment variables, see [Variable Expansion](https://github.com/jenkinsci/p4-plugin/blob/master/VARIABLEEXPANSION.md).  

Click the browser **Back** button to go back to the previous page. 
