About Internal Versioning, for the filesystem, since there are coordinator and worker, it is hard to make the config/catalogs files have same timestamp. I suggest introduce a new field, so that we can keep coordinator/worker has the same revision.

Good, for etcd, please keep the prefix "/presto" configuable

I want to use env variable to control whether the config and catalogs provided by filesystem or etcd, or it can be choosen using command parameters

presto-native-execution/presto_cpp/main/QueryContextManager.cpp updateFromSystemConfigs

https://github.com/prestodb/presto/blob/94b04cb21ba737e9d230ab5188802bc0aad0a4aa/presto-native-execution/presto_cpp/main/SessionProperties.cpp#L54C20-L54C37 SessionProperties

Presto java: presto-main-base/src/main/java/com/facebook/presto/SystemSessionProperties.java

Now, let's start from very beginning. 

First remember there is the challenge that presto have cooridnator and worker which are distributed, the catalog need sync in both side.

Next, create a proposals.md, propose 2 ways to do this.

1. Based on existing implementation, when a catalog is changing, put it to the pending list if still in use, next time if a new query want to use this catalog, it need wait until the catalog removed from the pending list. By using lock or mutex.

2. Use a versioned catalog manager, a current version inside the manager, each time catalog changed, it will update the version, make a snapshot of old version, and create a new map to store new version of catalogs. In this way, when a catalog is changed, the query doesn't need to wait.

Based on the 2 proposal, provide details about the catalog added/updated/deleted process. Especially to make it thread safe and distributed. 