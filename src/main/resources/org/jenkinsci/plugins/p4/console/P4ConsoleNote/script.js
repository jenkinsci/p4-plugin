function toggle(showHideDiv, switchTextDiv) {
	var tog = document.getElementById(showHideDiv);
	var text = document.getElementById(switchTextDiv);
	if(tog.style.display == "block") {
    	tog.style.display = "none";
		text.innerHTML = "expand";
  	}
	else {
		tog.style.display = "block";
		text.innerHTML = "collapse";
	}
}


