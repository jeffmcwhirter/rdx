
# Radiometrics RAMADDA
The Radiometrics RAMADDA  National Mesonet Program Monitor  is contained within a RAMADDA plugin. Below are instructions on how to build the plugin but as a convenience we make a release of the plugin in the <a href="lib">lib directory</a>.

 
This plugin runs under RAMADDA. 
To install RAMADDA on an AWS EC2 instance see <a href="https://geodesystems.com/repository/a/release#awsinstall">https://geodesystems.com/repository/a/release#awsinstall</a>.
You can also run RAMADDA in other environments including on your laptop. More information is provided 
<a href="https://geodesystems.com/repository/userguide/installing.html">here</a>.

Once RAMADDA is installed copy the <a href="https://github.com/jeffmcwhirter/rdx/blob/master/lib/rdxplugin.jar">rdxplugin.jar</a> to the RAMADDA plugin directory on your server at:
<pre>&lt;RAMADDA home&gt;/plugins</pre>
and restart the RAMADDA server.

The initial instrument collection and associated content is provided as a RAMADDA export file. This is based on the example content at <a href="https://geodesystems.com/repository/a/rdxnmp">https://geodesystems.com/repository/a/rdxnmp</a>. Download the
<a href="https://github.com/jeffmcwhirter/rdx/blob/master/lib/rdxexport.zip">rdxexport.zip</a> file. Then, from your RAMADDA (assuming you are logged in) choose a folder to hold the content then from the folder's popup menu button (<img width=20 src="https://geodesystems.com/repository/icons/entrymenu.png">) select Import and upload the rdxexport.zip file.

# Building the plugin
Building the  plugin relies on the RAMADDA source tree to be installed as a sibling of this  directory. Get the RAMADDA source with:
<pre>
git clone https://github.com/geodesystems/ramadda.git
</pre>

To build the plugin you need to have Java Ant installed (as well as Java JDK8+). Just run ant from the top-level directory.
<pre>
ant
</pre>

This builds dist/rdxplugin.jar and copies it into your local .ramadda/plugins directory. To release it to an external server you'll need to copy the plugin to the server's ramadda/plugins directory and restart the repository.


# Configuration

Along with the rdxplugin.jar plugin file there is some further configuration to do - specify the external RDX database, email and SMS settings.
These configuration options are set in the rdx.properties file 
(example <a href="rdx.properties">here</a>). 
If not already done so copy this file to your RAMADDA server's
<a href="https://geodesystems.com/repository/userguide/installing.html#home">home directory</a>.

## Configuring the RDX database

You will need to specify the JDBC URL for the external instrument status database. This takes the form of:
<pre>
rdx.db.url=jdbc:&lt;database type&gt;:&lt;database path&gt;
rdx.db.user=&lt;database user&gt;
rdx.db.password=&lt;database password&gt;
</pre>


e.g. for running with Postgres use:
<pre>
rdx.db.url=jdbc:postgresql://localhost/repository?UseServerSidePrepare=1&Protocol=-1
</pre>


## Configuring email settings
To send email notifications when running in AWS EC2 you need to do a couple of things. 
* Enable AWS SES. First,  you need to enable Amazon's <a href="https://docs.bitnami.com/aws/how-to/use-ses/">Simple Email Service</a> and verify your email addresses. 

* Configure RAMADDA. In  RAMADDA's  Admin Settings-Site and Contact Information specify an administrator email (as specified in SES) and provide the Mail server, e.g.:

<pre>
email-smtp.us-west-2.amazonaws.com
</pre>

In the external rdx.properties file on your server you need to specify the smtp user and password from the AWS-SES configuration:
<pre>
ramadda.admin.smtp.user=
ramadda.admin.smtp.password=
ramadda.admin.smtp.starttls=true
</pre>


## Configuring text message settings

To send text message notifications we use RAMADDA's phone plugin which is included in  the set of core plugins. If you have not installed the core plugins then build the phone plugin from the RAMADDA repository and copy it into the server's &lt;ramadda home dir&gt;/plugins directory.


The phone plugin uses <a href="https://www.twilio.com/">Twilio</a> as the message gateway. 
You will need to create an account with Twilio and configure your RAMADDA server. 
The 2 main properties to set in your rdx.properties are:
<pre>
#Twilio app id
twilio.appid=

#Twili authorization token (for reading the transcription)
twilio.authtoken=
</pre>


# Plugin contents
The plugin is in: <a href=src/com/radiometrics/plugin>src/com/radiometrics/plugin</a>

The contents are:

* api.xml - Defines the api endpoints into RdxApiHandler

* RdxApiHandler.java - This is a singleton class that gets instantiated at runtime. Implements the /rdx/status and /rdx/notifications pages. Also creates 2 threads  - one for monitoring the external instrument status database. One for handling notifications


* RdxInstrumentTypeHandler.java - Represents the instruments. Just does the decoration of the datetime values

* rdx.sql - Creates the test database table and the notification db

* rdxtypes.xml - Defines the entry types - rdx instrument collection, rdx instrument. Note - the default page display
for collections and instruments is defined within this xml file as an embedded <i>wiki</i> tag.

* metadata.xml - Defines the notification metadata

* rdxtemplate.html - provides the Radiometrics page template

* htdocs/rdx/ - Holds images, documentation page, etc

