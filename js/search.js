// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Core API",
      "url": "/binny/core",
      "content": "Core API The core module depends on the fs2 library to provide a convenient api. The main idea is to have a uniform api for storing/retrieving files across a variety of backends. The most focus is on sql databases. The jdbc and pglo module aim to provide an efficient way to store even large files in databases. But there is the minio module which provides a S3 compatible object storage backend. A file here is a Binary[F] which is a type alias for Stream[F, Byte]. The BinaryAttributes holds some attributes for a binary, the length, content type and sha256 hash. The BinaryStore[F] is the main entry point and defines storing/retrieving Binary[F]. Then this can be combined with a BinaryAttributesStore which does the same for BinaryAttributes objects. Utilities Logging Binny defines its own logger interface, to not impose a concrete one on you. You can implement Logger[F] based on some real logging library, like for example log4s; or just use the Logger.silent option for no logging and Logger.stdout option for logging to stdout. import binny.util.Logger import cats.effect.IO val loggingOff = Logger.silent[IO] // loggingOff: Logger[IO] = binny.util.Logger$$anon$2@5b3755f4 val stdout = Logger.stdout[IO]() // stdout: Logger[IO] = binny.util.Logger$$anon$3@248d3a"
    } ,    
    {
      "title": "Core Api - BinaryAttributeStore",
      "url": "/binny/core/attrstore",
      "content": "BinaryAttributeStore The case class BinaryAttributes describes some attributes that can be derived from binary data: sha256 hash, length and content type. In theory this can be re-created by reading through all binaries. The BinaryAttributeStore trait defines methods to store and retrieve these attributes given a BinaryId. trait BinaryAttributeStore[F[_]] { /** Associate the attributes to the key. If already exists, the data is replaced. */ def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit] /** Removes the attributes associated to this id, if existing. */ def deleteAttr(id: BinaryId): F[Boolean] /** Finds attributes by its id. */ def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] } Saving attributes takes a F[BinaryAttributes] to allow an “empty” implementation to ignore the computation of this data. All BinaryStore implementations take one of this as argument. You can always pass BinaryAttributeStore.none to not maintain attributes. A BinaryStore can create attributes when storing a byte stream. Many implementations here require a BinaryAttributeStore - but it’s optional, since BinaryAttributeStore.empty can be specified. Attributes can be retrieved separately from the data. Both, the bytes and the attributes, may be stored at different places. For example, MinIO could be used to store the blobs while a sql database could be used to store the attributes. The attributes table can then be integrated in applications properly, using libraries like slick or doobie."
    } ,    
    {
      "title": "Core Api - Binary Store",
      "url": "/binny/core/binarystore",
      "content": "BinaryStore The BinaryStore trait is the main entrypoint and defines very basic methods for storing and retrieving data. The binary data is represented as a Binary[F] (a type alias for Stream[F, Byte]) which can be referred to via a BinaryId. trait BinaryStore[F[_]] { /** Returns a set of ids currently available in this store. */ def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] /** Insert the given bytes creating a new id. */ def insert(hint: Hint): Pipe[F, Byte, BinaryId] /** Insert the given bytes to the given id. If some file already exists by this id, the * behavior depends on the implementation. */ def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] /** Finds a binary by its id. The range argument controls which part to return. */ def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] /** Check if a file exists. Same as `findBinary().isDefined`, but usually more * efficiently implemented. */ def exists(id: BinaryId): F[Boolean] /** Deletes all data associated to the given id. */ def delete(id: BinaryId): F[Unit] } It is capable of storing, deleting and retrieving data and attributes. Available ids can be listed, searching is not supported. Note that the findBinary allows to specify a range in order to retrieve only a part of some file."
    } ,    
    {
      "title": "Core API - ContentTypeDetect",
      "url": "/binny/core/detect",
      "content": "ContentTypeDetect The content type of a binary is always an interesting thing to know. It is (kind of) required when serving files from a http server. The content type is part of the BinaryAttributes. The ContentTypeDetect trait exists to plug in a detector for the content type. The detect method receives a portion of the data as a ByteVector and some hints - either a filename and/or an advertised content type. You can just use a ContentTypeDetect.none if you don’t bother. The core module provides an implementation based on the FileTypeDetector api present since Java7. This only looks at the filename if present. If no filename is given in the hint, it will always return application/octet-stream. import binny._ import scodec.bits.ByteVector ContentTypeDetect.probeFileType .detect(ByteVector.empty, Hint.filename(\"index.html\")) // res0: SimpleContentType = SimpleContentType(text/html) Note, the result above depends on the runtime. It can be modified by other jars in the class path, for example. For more reliable results, the binny-tikadetect module is recommended. Tika For a better outcome, the module binny-tikadetect provides a ContentTypeDetect implemenatiton based on the well known tika library. This also looks at the given bytes in conjunction with the provided hints. import binny._ import binny.tika._ import scodec.bits.ByteVector // using the filename only TikaContentTypeDetect.default .detect(ByteVector.empty, Hint.filename(\"index.html\")) // res1: SimpleContentType = SimpleContentType(text/html) // using the first view bytes TikaContentTypeDetect.default .detect(ByteVector.fromValidBase64(\"iVBORw0KGgoAAAANSUhEUgAAA2I=\"), Hint.none) // res2: SimpleContentType = SimpleContentType(image/png)"
    } ,    
    {
      "title": "FS",
      "url": "/binny/fs",
      "content": "File System This wraps the fs2 Files API to create a BinaryStore. Usage You need a FsStoreConfig object and a BinaryAttributeStore to create an instance of the FsBinaryStore. The companion object has some convenience constructors. The FsBinaryStore.default creates a store that saves files in a subdirectory hierarchy and stores the attributes in another file, next to the file containing the data. import binny._ import binny.fs._ import fs2.io.file.{Files, Path} import cats.effect.IO import cats.effect.unsafe.implicits._ import fs2.Stream val logger = binny.util.Logger.silent[IO] // logger: util.Logger[IO] = binny.util.Logger$$anon$2@44f6ac4f val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) // lets store two pieces and look at the outcome val run = for { baseDir &lt;- Stream.resource(DocUtil.tempDir) store = FsBinaryStore.default(logger, baseDir) id1 &lt;- someData.through(store.insert(Hint.none)) id2 &lt;- someData.through(store.insert(Hint.none)) layout &lt;- Stream.eval(DocUtil.directoryContentAsString(baseDir)) } yield (id1, id2, layout) // run: Stream[IO[x], (BinaryId, BinaryId, String)] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: (BinaryId, BinaryId, String) = ( // BinaryId(71nMnNRGJq2sNbsN5CZ17Tj7B6zx2QCD4gEBuFurubES), // BinaryId(6BhJTVsoVFFajtaLdYw68TWDsH6gckQeABGCCFKZVDXL), // \"\"\" // 📁 binny-docs-12873910996676467204 // 📁 6B // 📁 6BhJTVsoVFFajtaLdYw68TWDsH6gckQeABGCCFKZVDXL // · file // · attr // 📁 71 // 📁 71nMnNRGJq2sNbsN5CZ17Tj7B6zx2QCD4gEBuFurubES // · file // · attr\"\"\" // ) This store uses the id to create a directory using the first two characters and another below using the complete id. Then the data is stored in file and its attributes in attr. The store also uses the FsBinaryAttributeStore to store the attributes in the file system. This has been configured by the same strategy to have both files next to each other. This can be changed by providing a different FsStoreConfig and FsAttrConfig, respectively. Of course, a different implementation of BinaryAttributeStore can be used as well. The mapping of an id to a file in the filesystem is given by a PathMapping. There are some provided, the above results are from PathMapping.subdir2. As another example, the next FsBinaryStore won’t store any attributes and puts the files directly into the baseDir - using the id as its name. val run2 = Stream.resource(DocUtil.tempDir).flatMap { baseDir =&gt; val store = FsBinaryStore[IO]( FsStoreConfig.default(baseDir).withMapping(PathMapping.simple), logger, BinaryAttributeStore.empty[IO] ) someData.through(store.insertWith(BinaryId(\"hello-world.txt\"), Hint.none)) ++ someData.through(store.insertWith(BinaryId(\"hello_world.txt\"), Hint.none)) ++ Stream.eval(DocUtil.directoryContentAsString(baseDir)) } // run2: Stream[IO[x], String] = Stream(..) run2.compile.lastOrError.unsafeRunSync() // res1: String = \"\"\" // 📁 binny-docs-12112031525786055854 // · hello-world.txt // · hello_world.txt\"\"\" A PathMapping is a function (Path, BinaryId) =&gt; Path) where the given path is the base directory. So you can easily create a custom layout."
    } ,    
    {
      "title": "Implementation Modules",
      "url": "/binny/impls",
      "content": "Implementation Modules These modules provide implementations for BinaryStore and some also for BinaryAttributeStore. file system wrapping the fs2 Files API JDBC provides a generic implementation using pure JDBC PostgreSQL Large Object using PostgreSQLs Large Objects MinIO provides a S3 compatible store based on the SDK from MinIO"
    } ,    
    {
      "title": "Home",
      "url": "/binny/",
      "content": "Binny Binny is a Scala library for efficiently storing and retrieving (large) binary data from databases, providing a FS2 based api. Binny is provided for Scala 2.13 and 3. Usage With sbt, add the dependencies: \"com.github.eikek\" %% \"binny-core\" % \"0.4.0\" // the core library // … choose one or more implementation modules \"com.github.eikek\" %% \"binny-fs\" % \"0.4.0\" // implementation based on FS2 `Files` api \"com.github.eikek\" %% \"binny-jdbc\" % \"0.4.0\" // implementation based on JDBC \"com.github.eikek\" %% \"binny-pglo\" % \"0.4.0\" // implementation based on PostgreSQLs LargeObject API \"com.github.eikek\" %% \"binny-minio\" % \"0.4.0\" // implementation for MinIO License This project is distributed under the MIT The logo is from here."
    } ,    
    {
      "title": "JDBC",
      "url": "/binny/jdbc",
      "content": "Generic JDBC This module provides a BinaryStore and BinaryAttributeStore using JDBC. It is tested for H2, PostgreSQL and MariaDB only, but other database systems probably work as well. The module doesn’t include any JDBC driver, you need to pull in the one you want to use in your project. The approach is as follows: the byte stream is split in chunks (size can be configured) and each chunk is stored in a blob column and associated to the same binary id. Table Structure Since the blob datatype characteristics differ between databases, you might want to define the table yourself or use the provided setup for PostgreSQL or MariaDB. See the CreateDataTable for the table definitions. Here is the definition for PostgreSQL: CREATE TABLE IF NOT EXISTS \"file_chunk\" ( \"file_id\" varchar(254) not null, \"chunk_nr\" int not null, \"chunk_len\" int not null, \"chunk_data\" bytea not null, primary key (\"file_id\", \"chunk_nr\") ) This makes range requests efficient, since it is possible to calculate at what chunk to start (and to end if applicable). The table names are just examples, they can be specified when creating a store. Chunksize when storing and streaming A caveat here is that the chunksize used to store a file, also determines the amount of memory used when reading the file. It is not possible to store in 512k chunks and then load it in 10k chunks, for example. The reason is that many jdbc drivers (at least these I know) don’t support streaming from a blob. You’ll get the whole blob in a byte array anyways. So this cannot be changed after a file has been stored. When streaming, the blob of each row is loaded in memory, one at a time. Its size defines the amount of memory used to stream a file. If you’re using PostgreSQL, consider the pglo module which doesn’t have this restriction. Usage You need to provide a javax.sql.DataSource. How to create this is out of scope for this project. A JdbcStoreConfig is required, that defines some settings, like the table name and chunk size to use. For the examples here, an in-memory database (H2) is used. import binny._ import binny.util.Logger import binny.Binary.Implicits._ import binny.jdbc._ import cats.effect.IO import cats.effect.unsafe.implicits._ val dataSource = ConnectionConfig.h2Memory(\"docs\").dataSource // dataSource: javax.sql.DataSource = ds0: url=jdbc:h2:mem:docs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1 user=sa implicit val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@181b8d0c val store = GenericJdbcStore.default[IO](dataSource, Logger.silent[IO]) // store: GenericJdbcStore[IO] = binny.jdbc.GenericJdbcStore$Impl@376a34fb // creates the schema, the filenames are from the default config DatabaseSetup.runBoth[IO](Dbms.H2, dataSource, \"file_chunk\", \"file_attr\").unsafeRunSync() // res0: Int = 0 val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) val id = someData.through(store.insert(Hint.filename(\"test.txt\"))) .compile.lastOrError.unsafeRunSync() // id: BinaryId = BinaryId(En4BJM6c155jXerH2YTYf4h428wocRFV56q6megex2FR) // get the file out store.findBinary(id, ByteRange.All).getOrElse(sys.error(\"not found\")) .flatMap(binary =&gt; binary.readUtf8String) .unsafeRunSync() .take(50) // res1: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello wo\"\"\" The default setup also stores the attributes in the same database in a different table. val attrStore = JdbcAttributeStore(JdbcAttrConfig.default, dataSource, logger) // attrStore: JdbcAttributeStore[IO[A]] = binny.jdbc.JdbcAttributeStore@5d63d553 attrStore.findAttr(id).getOrElse(sys.error(\"not found\")).unsafeRunSync() // res2: BinaryAttributes = BinaryAttributes( // sha256 = Chunk( // bytes = View( // at = scodec.bits.ByteVector$AtArray@5e147821, // offset = 0L, // size = 32L // ) // ), // contentType = SimpleContentType(text/plain), // length = 1978876L // ) JdbcBinaryStore As seen above, the store is an instance of the trait JdbcBinaryStore. It extends BinaryStore to add a findBinaryStateful method. The “state” relates to the connection to the database. The default findBinary method uses one connection per chunk. This allows to free resources each time the stream is pulled. Otherwise timeouts could occur, if for example the stream is not being pulled for a while. When a network client is consuming the stream with a slow connection, reading one chunk takes a while and could lead to the connection being closed (by the pool or server). However, if you know to process only small files or consume the data fast, it is possible to stream the whole file using a single connection, which is faster. This is provided by findBinaryStateful. ChunkedBinaryStore The GenericJdbcStore also implements ChunkedBinaryStore to allow storing chunks independently. This is useful if chunks are received in random order and the whole file is not available as complete stream. However, in order to use this the complete size of the file must be known up front. This is needed to know when the last chunk is received. JdbcAttributeStore The JdbcAttributeStore can be used to store BinaryAttributes in the database. If your application uses a sql database, it might be useful to have the attributes in the database to build on it. The data can be stored somewhere else, if desired. The JdbcAttributeStore can be used with different BinaryStore implementations. Here is the table for storing attributes (PostgreSQL example): CREATE TABLE IF NOT EXISTS \"file_attr\" ( \"file_id\" varchar(254) not null, \"sha256\" varchar(254) not null, \"content_type\" varchar(254) not null, \"length\" bigint not null, primary key (\"file_id\") )"
    } ,      
    {
      "title": "MinIO",
      "url": "/binny/minio",
      "content": "MinIO MiniO is a S3 compatible object storage which can be used to back a BinaryStore. It is available under a free license and can be self hosted. This module provides an implementation that is based on MinIOs Java SDK. The SDK provides an easy to use, blocking http client. It is wrapped in a Sync and executed on the blocking pool. Usage To create such a store, you only need the endpoint url, the credentials (an access- and secret key) and a mapping from a BinaryId to a pair of values, S3Key. The MinIO store expects its key to have a bucket and a filekey (or object id). Since a BinaryId is just one value, the store must know how to create the two values, bucket name and object id - which are combined in a S3Key. The example below starts a container running MinIO in order to work. The container knows the endpoint and keys and can create a config for creating the store. It’s only necessary to specify the mentioned mapping. Here we use a constant bucket name docs-bucket. import binny._ import binny.Binary.Implicits._ import binny.minio._ import binny.util.Logger import fs2.Stream import cats.effect.IO import cats.effect.unsafe.implicits._ val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@4c0ddace val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) val run = for { // start a MiniO container, which can create a valid config minio &lt;- Stream.resource(DocUtil.startMinIOContainer) config: MinioConfig = minio.createConfig(S3KeyMapping.constant(\"docs-bucket\")) // Create the `BinaryStore` store = MinioBinaryStore[IO](config, BinaryAttributeStore.empty[IO], logger) // insert some data id &lt;- someData.through(store.insert(Hint.none)) // get the file out bin &lt;- Stream.eval( store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error(\"not found\")) ) str &lt;- Stream.eval(bin.readUtf8String) } yield str + \"...\" // run: Stream[IO[x], String] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello world 4 // hello world 5 // hello world 6 // hello world 7 // he...\"\"\""
    } ,    
    {
      "title": "PGLO",
      "url": "/binny/pglo",
      "content": "PostgreSQL Large Objects This module utilises PostgreSQLs Large Objects to implement a BinaryStore. This is then only for PostgreSQL, and it also depends on the postgresql jdbc driver. Using large objects, postgresql stores the data outside its standard table space and the object can be referenced by an id. They also allow to seek into a specific position, which is used to implement loading partial data. The PgLoBinaryStore also implements JdbcBinaryStore and provides two methods to retrieve a binary. One, the default findBinary, uses one connection per chunk. The other, findBinaryStateful uses a connection for the entire stream. Table Structure The table used here is: CREATE TABLE IF NOT EXISTS \"file_lo\" ( \"file_id\" varchar(254) NOT NULL PRIMARY KEY, \"data_oid\" oid NOT NULL ) Usage For the examples to run, a PostgreSQL server is necessary. It is quite easy to start one locally, for example with docker. import binny._ import binny.util.Logger import binny.Binary.Implicits._ import binny.jdbc.ConnectionConfig import binny.pglo._ import fs2.Stream import cats.effect.IO import cats.effect.unsafe.implicits._ implicit val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@4c9e86a4 val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) val run = for { // start a postgres container and create a DataSource that connects to it postgres &lt;- Stream.resource(DocUtil.startPostgresContainer) ds = ConnectionConfig(postgres.jdbcUrl, postgres.username, postgres.password).dataSource // Create the `BinaryStore` and the schema store = PgLoBinaryStore.default[IO](logger, ds) _ &lt;- Stream.eval(PgSetup.run[IO](store.config.table, logger, ds)) // insert some data id &lt;- someData.through(store.insert(Hint.none)) // get the file out bin &lt;- Stream.eval( store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error(\"not found\")) ) str &lt;- Stream.eval(bin.readUtf8String) } yield str + \"...\" // run: Stream[IO[x], String] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello world 4 // hello world 5 // hello world 6 // hello world 7 // he...\"\"\" JdbcBinaryStore The PgLoBinaryStore also provides a findBinaryStateful variant just like the GenericJdbcStore. The default findBinary method creates a byte stream that loads the file in chunks. After every chunk, the connection is closed again and the next chunk seeks into the large object to start anew. In contrast, the findBinaryStateful method uses a single connection for the entire stream."
    } ,      
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
