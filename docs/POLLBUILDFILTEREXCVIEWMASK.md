# Exclude Changes Outside View Mask
Changes can be filtered to not trigger a build if none of the files within a change are contained in the view mask. 
![Exclude Changes Outside View Mask](docs/images/pollbuildfilterexcviewmask.png)
**For example, with a view mask filter of:**  
`//depot/main/tests`  
`-//depot/main/tests/001`
- The change below is not filtered for the files below because `index.xml` is in the view mask:
  `//depot/main/tests/index.xml`
 `//depot/main/tests/001/test.xml`
- The change below is not filtered for the files below because `index.xml` is in the view mask:
`//depot/main/test/index.xml`
 `//depot/main/src/build.xml`
- The change below is filtered because `build.xml` is not in the view mask: 
`//depot/main/src/build.xml`
- The change below is filtered because no file is in the view mask:
`//depot/main/src/build.xml`
`//depot/main/tests/001/test.xml`

Click the browser **Back** button to go back to the previous page. 
