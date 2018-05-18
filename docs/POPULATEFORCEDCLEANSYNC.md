# Forced Clean and Sync
This option is Not recommended because it is very inefficient. Used if you do not trust the content of your workspace. Removes all of the files in the workspace root, including the build history, and then force syncs the required files. 
![Forced Clean and Sync](images/populateforcedcleansync.png)

## Options:
You can select a number of options:
- **Populate have list:** Overwrites the Perforce "have list". 
- **QUIET Perforce messages:** Enables the -q flag for all applicable Perforce Helix Core Server operations. Summary details will still be displayed.
- **Pin build at Perforce Label:** When a build is triggered by **Polling**, **Build Now** or an external action, the workspace will only sync to the Perforce label in this field. Any other specified change or label will be ignored.
Supports variable expansion, for example `${VAR}`. If *now* is used, or a variable that expands to *now*, the latest change is used (within the scope of the workspace view). For more information about environment variables, see [Variable Expansion](VARIABLEEXPANSION.md).  

Click the browser **Back** button to go back to the previous page. 
