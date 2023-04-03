# Aleksandr.Tsarev random thoughts
1. Liked idea about generating "effective" config file.
2. Partially liked idea about "inheritance", but with limitations.
3. Liked @anton.prokhorov idea about templating, treated it for toml like:
   We can define some "template", like:
   ```toml
   # extension_based.toml
   [sources]
   path = [ "**/*.kt" ]
   
   [target.ios.sources]
   path = [ "**/*.ios.kt" ]
   
   [target.jvm.sources]
   path = [ "**/*.jvm.kt" ]
   ```
   And then use it in build file, like:
   ```toml
   # build.toml
   sources = [ "extension_based", "directory_based" ] # tread equation instead of table definition as template.
   ```