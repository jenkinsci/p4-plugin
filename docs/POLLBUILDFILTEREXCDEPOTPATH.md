# Exclude Changes From Depot Path
Changes can be filtered to not trigger a build if all of the files within a change match the specified path.  
![Exclude Changes From Depot Path](docs/images/pollbuildfilterexcdepotpath.png)
**For example, with a filter of:**
`//depot/main/tests`:
- The change below is filtered because all of the files within the the change match the filter path:
`//depot/main/tests/index.xml`
`//depot/main/tests/001/test.xml`
`//depot/main/tests/002/test.xml`
- The change below is not filtered because `build.xml` is outside of the filter path:
`//depot/main/src/build.xml`
`//depot/main/tests/004/test.xml`
`//depot/main/tests/005/test.xml`

Click the browser **Back** button to go back to the previous page. 
