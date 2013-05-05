var working = null;
var showing = null;

function loadImage(e) {
	var oldshowing = showing;
	showing = working;
	working = oldshowing;
	showing.unbind();
	showing.css("zIndex", 1);
}

function processVideo() {
		working.css("zIndex", -1);
		working.load(loadImage);
		working.attr("src", "live.jpg?rnd="
				+ Math.floor(Math.random() * 1000000));
}

function initVideo()
{
  working = $("#img1");
  showing = $("#img2");
  processVideo();
}
