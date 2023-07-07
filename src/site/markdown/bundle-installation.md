# Bundle Installation/Uninstallation

There are three different installation approaches supported by the plugin which behave differently for the installation and uninstallation. In the following section all mechanisms are outlined individually for both use cases.

<!-- MACRO{toc} -->

## Deploy/Upload/Install

### Felix Web Console

The plugin by default places an *HTTP POST* request to the [Felix Web Console](https://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html#post-requests). This will achieve both upload and installation/update of the bundle triggered by one request. The installation/update of the bundle happens *asynchronously*, though, in the Felix Web Console.

### WebDAV PUT

It's also possible to use *HTTP PUT* instead of POST leveraging the [WebDAV bundle from Sling](https://sling.apache.org/documentation/development/repository-based-development.html). This will create a new JCR node containing the OSGi bundle in the underlying repository.
From there it needs to be picked up by the [JCR Installer](https://sling.apache.org/documentation/bundles/jcr-installer-provider.html) to be actually installed in the OSGi container. The latter happens *asynchronously*. It is important that the bundle resource is uploaded to a location of the repository which is actually watched by the JCR installer (by default only resources below a resource called `install`).

### Sling POST Servlet

Since version 2.1.8 you can also leverage the [Sling POST servlet](https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html) for uploading the bundle to the repository. The subsequent installation is being performed *asynchronously* by the JCR Installer (similar to the WebDAV PUT approach).

## Undeploy/Uninstall

### Felix Web Console

Places an *HTTP POST* request to the [Felix Web Console](https://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html#post-requests). This will achieve 
uninstallation of the bundle *synchronously*.

### WebDAV PUT

An *HTTP DELETE* request is issued which is handled by the [WebDAV bundle from Sling](https://sling.apache.org/documentation/development/repository-based-development.html). This will remove the JCR node containing the OSGi bundle in the underlying repository.
From there it needs to be picked up by the [JCR Installer](https://sling.apache.org/documentation/bundles/jcr-installer-provider.html) to be actually uninstalled in the OSGi container. The latter happens *asynchronously*.

### Sling POST Servlet

An *HTTP DELETE* request is issued which is handled by the [Sling POST servlet](https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html). This will remove the resource containing the OSGi bundle in the underlying repository. The subsequent uninstallation is being performed *asynchronously* by the JCR Installer (similar to the WebDAV PUT approach).

## Intermediate Resources

For both WebDAV PUT and Sling POST servlet installation, intermediate resources (i.e. non-existing parent resources) 
are automatically created. The primary type of those intermediate nodes depend on the deployment method (only applicable if the JCR resource provider is used)

- **WebDAV PUT**, uses the configured collection node type, by default `sling:Folder` (see also [WebDAV Configuration](https://sling.apache.org/documentation/development/repository-based-development.html))
- **Sling POST Servlet**, uses internally `ResourceResolverFactory.create(...)` without setting any `jcr:primaryType`. Therefore the `JcrResourceProviderFactory` will call `Node.addNode(String relPath)` which determines a suitable 
node type automatically, depending on the parent's node type definition (see [JCR Node Javadoc](https://s.apache.org/jcr-2.0-javadoc/javax/jcr/Node.html#addNode(java.lang.String))).

In most of the cases the intermediate node is of type  `sling:Folder`, as this is the first allowed child node definition for node type `sling:Folder`. This may only differ if your existing parent node is not of type `sling:Folder` itself.
