<?php

/*

This file is part of tourrobot - https://github.com/jpralves/tourrobot

tourrobot is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License.

tourrobot is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with tourrobot.  If not, see <http://www.gnu.org/licenses/>.

*/

require_once('config.php');

// http://jpralves.net/51/?listrecords=1

$bd = "";
if (isset($_REQUEST["ip"])) {
	echo getclientip();
	exit();
}

if (!empty($_REQUEST["sk"]) && ($_REQUEST["sk"] == $GLOBALS['sitekey'])) {
	$info = $_REQUEST["i"];
	$version = $_REQUEST["v"];
	if (!empty($_REQUEST["nl"])) {
		setnewlocation($_REQUEST["nl"], $info, $version);
		echo "OK";
	}
	if (!empty($_REQUEST["dl"])) {
		disablelastlocation();
		echo "OK";
	}
	exit();
}

if (!empty($_REQUEST["listrecords"])) {
	echo "<HTML><HEAD></HEAD><BODY>";
	listrecords();
	echo "</BODY></HTML>";
	exit();
}

$newlocation = getnewlocation();
if ($newlocation == "") {
	include("unavailable.html");
} else {
	header("Location: " . $newlocation);
}
exit();

/**
 * gets the public IP Address of the caller
 * 
 * @return String with IP Address
 */
function getclientip() {
	return $_SERVER['REMOTE_ADDR'];
}

/**
 * sets the database connection
 * 
 * @param boolean $moderw - defines if connection os Read-Only or Read-Write
 * @return PDO
 */
function setup_database($moderw) {

	if ($moderw) {
		$user = $GLOBALS['usernameRW'];
		$pass = $GLOBALS['passwordRW'];
	} else {
		$user = $GLOBALS['usernameRO'];
		$pass = $GLOBALS['passwordRO'];
	}
	global $db;
	try {

		$db = new PDO(
				'mysql:host='.$GLOBALS['database_server'].';dbname='.$GLOBALS['database_name'],
				$user,
				$pass,
				array( PDO::ATTR_PERSISTENT => false,PDO::ATTR_EMULATE_PREPARES=> false)
				
		);
		// $db->exec("SET CHARACTER SET utf8");
	}
	catch(PDOException $e) {
		print $e->getMessage();
		die("Could not connect to the database.");
	}
	return $db;
}

/**
 * Closes database
 */
function close_database() {
	unset($db);
}

/**
 * gets the new location from the database
 * 
 * @return String with url destination
 */
function getnewlocation() {
	$db = setup_database(false);
	$stmt = $db->prepare("SELECT url,datahora,active FROM nxtcontrollersite ORDER BY datahora desc limit 1");
	$stmt->execute();
	$rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
	if ($rows) {
		if ($rows[0]['active']) {
			$result  = $rows[0]['url'];
		}
	}
	close_database();
	return $result;
}

/**
 * Setups a new location
 * 
 * @param string $newloc
 * @param string $info
 * @param string $version
 */
function setnewlocation($newloc, $info, $version) {

	$clientip = getclientip();
	
	if (strpos($newloc,'@PUBIP@') !== false) {
		$newloc = str_replace("@PUBIP@", $clientip, $newloc);
	}
	
	$db = setup_database(true);
	$stmt = $db->prepare("INSERT INTO nxtcontrollersite (url, datahora, endip, info, version) VALUES (?,UTC_TIMESTAMP(),?,?,?)");
	$stmt->execute(array($newloc, $clientip, $info, $version));
	close_database();
}

/**
 * Disables the Location redirector
 */
function disablelastlocation() {
	
	$db = setup_database(true);
	$stmt = $db->prepare("SELECT id FROM nxtcontrollersite ORDER BY datahora desc limit 1");
	$stmt->execute();
	$rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
	if ($rows) {
		$stmt = $db->prepare("UPDATE nxtcontrollersite set active = 0,edatahora = UTC_TIMESTAMP() WHERE ID =?");
		$stmt->execute(array($rows[0]['id']));
	}
	close_database();
}

/**
 * Lists records in the database
 */
function listrecords() {
	$db = setup_database(false);

	$stmt = $db->prepare("SELECT * FROM nxtcontrollersite ORDER BY datahora asc");
	$stmt->execute();

	$row = $stmt->fetch(PDO::FETCH_ASSOC);
	if ($row) {
		echo "<TABLE BORDER=1>";
		echo "<TR>";
		foreach($row AS $key=>$val) {
			echo "<TD>" . $key . "</TD>";
		}
		echo "</TR>\n";
		do {
			echo "<TR>";
			foreach($row AS $key=>$val) {
				echo "<TD>" . $val . "</TD>";
			}
			echo "</TR>\n";
		} while($row = $stmt->fetch(PDO::FETCH_ASSOC));
		echo "</TABLE>\n";
	}
	close_database();
}

?>
