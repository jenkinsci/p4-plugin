function toggle(showHideDiv, switchTextDiv) {
	var tog = document.getElementById(showHideDiv);
	var text = document.getElementById(switchTextDiv);
	if(tog.style.display == "block") {
    	tog.style.display = "none";
		text.innerHTML = "+";
  	}
	else {
		tog.style.display = "block";
		text.innerHTML = "-";
	}
}


