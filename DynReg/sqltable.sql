SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";

--
-- Database: `jpralves2generic`
--

-- --------------------------------------------------------

--
-- Table structure for table `nxtcontrollersite`
--

CREATE TABLE `nxtcontrollersite` (
  `ID` int(11) NOT NULL auto_increment,
  `url` varchar(100) NOT NULL,
  `datahora` datetime NOT NULL,
  `endip` varchar(15) NOT NULL,
  `edatahora` datetime default NULL,
  `info` varchar(200) default NULL,
  `version` varchar(15) default NULL,
  `active` tinyint(1) NOT NULL default '1',
  `lastdttouch` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`ID`),
  KEY `datahora` (`datahora`)
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;

