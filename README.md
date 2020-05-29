This holds the RAMADDA plugin for Radiometrics.
This plugin relies on the RAMADDA source tree to be installed as a sibling of this  directory. 
Get the RAMADDA source with:
git clone https://github.com/geodesystems/ramadda.git

# Building
To build you need to have Java Ant installed (as well as Java JDK8+). Just run ant from the top-level directory.
ant

This builds dist/rdxplugin.jar


# Contents
The RDX plugin is in:
src/com/radiometrics/plugin

* RdxApiHandler.java
Singleton that gets instantiated at runtime. Implements the /rdx/status page. Also creates 2 threads  - one for monitoring the external instrument status database. One for handling notifications

* RdxInstrumentTypeHandler.java
Represents the instruments. Currently no functionality

* api.xml
Defines the api endpoints into RdxApiHandler

* instruments.txt
Holds example instruments for testing

* metadata.xml
Defines the notification metadata

* rdx.properties
Placeholder

* rdx.sql
Creates the test database table and the notification db

* rdxtypes.xml
Defines the entry types - rdx instrument collection, rdx instrument

* htdocs/rdx/
Holds images, documentation page, etc


