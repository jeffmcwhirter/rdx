#Radiometrics RAMADDA

This holds the RAMADDA plugin for Radiometrics. This plugin relies on the RAMADDA source tree to be installed as a sibling of this  directory. Get the RAMADDA source with:
<pre>
   git clone https://github.com/geodesystems/ramadda.git
</pre>

# Building
To build you need to have Java Ant installed (as well as Java JDK8+). Just run ant from the top-level directory.
<pre>
ant
</pre>

This builds dist/rdxplugin.jar and copies it into your local .ramadda/plugins directory. To release it to an external server you'll need to copy the plugin to the server's ramadda/plugins directory.


# Installing

Along with the rdxplugin.jar you will need to specify the JDBC URL for the external instrument status database. This takes the form of:
<pre>
rdx.db.url=jdbc:&lt;database type&gt=;:&lt;database path&gt;
</pre>

e.g. for running with Derby use:
<pre>
rdx.db.url=jdbc:derby:/Users/jeffmc/.ramadda/derby/repository;create=true;
</pre>

# Email notifications
To send email notifications when running in AWS EC2 you need to do a couple of things. 
* Enable AWS SES. First,  you need to enable Amazon's <a href="https://docs.bitnami.com/aws/how-to/use-ses/">Simple Email Service</a> and verify your email addresses. 

* Configure RAMADDA. In  RAMADDA's  Admin Settings-Site and Contact Information specify an administrator email (as specified in SES) and provide the Mail server, e.g.:

<pre>
email-smtp.us-west-2.amazonaws.com
</pre>

In the external repository.properties file on your server you need to specify the smtp user and password from the AWS-SES configuration:
<pre>
ramadda.admin.smtp.user=
ramadda.admin.smtp.password=
ramadda.admin.smtp.starttls=true
</pre>


# Text message notifications

To send text message notifications we use RAMADDA's phone plugin which is included in  the set of core plugins. If you have not installed the core plugins then build the phone plugin from the RAMADDA repository and copy it into the server's ramadda/plugins directory.


The phone plugin uses <a href="https://www.twilio.com/">Twilio</a> as the message gateway. 
You will need to create an account with Twilio and configure your RAMADDA server. 
See <a href="https://geodesystems.com/repository/phone/configuration.html">here</a> for RAMADDA configuration information. The 2 main properties to set in ramadda/repository.properties are:
<pre>
#Twilio app id
twilio.appid=

#Twili authorization token (for reading the transcription)
twilio.authtoken=
</pre>

# Repository contents
The RDX plugin is in:

  src/com/radiometrics/plugin

* rdx.properties

Placeholder file for defining the external RDX db. Copy this into your RAMADDA home directory and edit it with your DB info


* api.xml

Defines the api endpoints into RdxApiHandler

* RdxApiHandler.java

This is a singleton class that gets instantiated at runtime. 
Implements the /rdx/status and /rdx/notifications pages. 
Also creates 2 threads  - one for monitoring the external instrument status database. One for handling notifications


* RdxInstrumentTypeHandler.java

Represents the instruments. Just does the decoration of the datetime values


* metadata.xml

Defines the notification metadata


* rdx.sql

Creates the test database table and the notification db

* rdxtypes.xml

Defines the entry types - rdx instrument collection, rdx instrument

* htdocs/rdx/

Holds images, documentation page, etc

* instruments.txt

Holds example instruments for population the test instrument db

