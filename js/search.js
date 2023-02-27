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
      "content": "Core API The core module depends on the fs2 library to provide a convenient api. The main idea is to have a uniform api for storing/retrieving files across a variety of backends. The most focus is on sql databases. The jdbc and pglo module aim to provide an efficient way to store even large files in databases. But there is the minio module which provides a S3 compatible object storage backend. A file here is a Binary[F] which is a type alias for Stream[F, Byte]. The BinaryAttributes holds some attributes for a binary, the length, content type and sha256 hash. The BinaryStore[F] is the main entry point and defines storing/retrieving Binary[F]. BinaryAttributes can be computed for any Binary[F], but a BinaryStore can provide a more efficient way. Utilities Logging Binny defines its own logger interface, to not impose a concrete one on you. You can implement Logger[F] based on some real logging library, like for example log4s; or just use the Logger.silent option for no logging and Logger.stdout option for logging to stdout. import binny.util.Logger import cats.effect.IO val loggingOff = Logger.silent[IO] // loggingOff: Logger[IO] = binny.util.Logger$$anon$2@3d764641 val stdout = Logger.stdout[IO]() // stdout: Logger[IO] = binny.util.Logger$$anon$3@71d99732"
    } ,    
    {
      "title": "Core Api - Binary Store",
      "url": "/binny/core/binarystore",
      "content": "BinaryStore The BinaryStore trait is the main entrypoint and defines very basic methods for storing and retrieving data. The binary data is represented as a Binary[F] (a type alias for Stream[F, Byte]) which can be referred to via a BinaryId. trait BinaryStore[F[_]] { /** Returns a set of ids currently available in this store. */ def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] /** Insert the given bytes creating a new id. */ def insert: Pipe[F, Byte, BinaryId] /** Insert the given bytes to the given id. If some file already exists by this id, the * behavior depends on the implementation. */ def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] /** Finds a binary by its id. The range argument controls which part to return. */ def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] /** Check if a file exists. Same as `findBinary().isDefined`, but usually more * efficiently implemented. */ def exists(id: BinaryId): F[Boolean] /** Deletes all data associated to the given id. */ def delete(id: BinaryId): F[Unit] /** Retrieves a selected set of attributes of a binary. */ def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] } It is capable of storing, deleting and retrieving data and attributes. Available ids can be listed, searching is not supported. Note that the findBinary allows to specify a range in order to retrieve only a part of some file."
    } ,    
    {
      "title": "Core Api - Chunked Binary Store",
      "url": "/binny/core/chunkedstore",
      "content": "ChunkedBinaryStore The ChunkedBinaryStore trait extends BinaryStore and adds one method: /** A BinaryStore that can also store chunks out of order. */ trait ChunkedBinaryStore[F[_]] extends BinaryStore[F] { /** Inserts the given chunk. This method allows to store chunks out of order. It is * required to specify the total amount of chunks; thus the total length of the file * must be known up front. * * The first chunk starts at index 0. * * The maximum chunk size is constant and defined by the implementation. All chunks * must not exceed this length. If the complete file consists of multiple chunks, then * only the last one may have less than this size. */ def insertChunk( id: BinaryId, chunkDef: ChunkDef, hint: Hint, data: ByteVector ): F[InsertChunkResult] } This allows storing a file in chunks and these chunks may arrive out of order. To have them concatenated correctly to the whole file, the chunk number must be provided and either the complete file length or the total amount of chunks. Not all implementation modules provide this type of store."
    } ,    
    {
      "title": "Core API - ContentTypeDetect",
      "url": "/binny/core/detect",
      "content": "ContentTypeDetect The content type of a binary is always an interesting thing to know. It is (kind of) required when serving files from a http server. The content type is part of the BinaryAttributes. The ContentTypeDetect trait exists to plug in a detector for the content type. The detect method receives a portion of the data as a ByteVector and some hints - either a filename and/or an advertised content type. You can just use a ContentTypeDetect.none if you don’t bother. The core module provides an implementation based on the FileTypeDetector api present since Java7. This only looks at the filename if present. If no filename is given in the hint, it will always return application/octet-stream. import binny._ import scodec.bits.ByteVector ContentTypeDetect.probeFileType .detect(ByteVector.empty, Hint.filename(\"index.html\")) // res0: SimpleContentType = SimpleContentType(text/html) Note, the result above depends on the runtime. It can be modified by other jars in the class path, for example. For more reliable results, the binny-tikadetect module is recommended. Tika For a better outcome, the module binny-tikadetect provides a ContentTypeDetect implemenatiton based on the well known tika library. This also looks at the given bytes in conjunction with the provided hints. import binny._ import binny.tika._ import scodec.bits.ByteVector // using the filename only TikaContentTypeDetect.default .detect(ByteVector.empty, Hint.filename(\"index.html\")) // res1: SimpleContentType = SimpleContentType(text/html) // using the first view bytes TikaContentTypeDetect.default .detect(ByteVector.fromValidBase64(\"iVBORw0KGgoAAAANSUhEUgAAA2I=\"), Hint.none) // res2: SimpleContentType = SimpleContentType(image/png)"
    } ,    
    {
      "title": "FS",
      "url": "/binny/fs",
      "content": "File System This wraps the fs2 Files API to create a BinaryStore. Usage You need a FsStoreConfig object to create an instance of the FsBinaryStore. The companion object has some convenience constructors. The FsBinaryStore.default creates a store that saves files in a subdirectory hierarchy. import binny._ import binny.fs._ import fs2.io.file.{Files, Path} import cats.effect.IO import cats.effect.unsafe.implicits._ import fs2.Stream val logger = binny.util.Logger.silent[IO] // logger: util.Logger[IO] = binny.util.Logger$$anon$2@52ec5ba6 val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) // lets store two pieces and look at the outcome val run = for { baseDir &lt;- Stream.resource(DocUtil.tempDir) store = FsBinaryStore.default(logger, baseDir) id1 &lt;- someData.through(store.insert) id2 &lt;- someData.through(store.insert) layout &lt;- Stream.eval(DocUtil.directoryContentAsString(baseDir)) } yield (id1, id2, layout) // run: Stream[[x]IO[x], (BinaryId, BinaryId, String)] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: (BinaryId, BinaryId, String) = ( // BinaryId(H8QZY941vLgEaeCpMBEf9LxiiexU7FF8YDZRTyLgJSo3), // BinaryId(3HNfwAqd6RKoHzH9524fP4tsJh9uYVy3tteUfreA6qqN), // \"\"\" // 📁 binny-docs-3363400676315580922 // 📁 3H // 📁 3HNfwAqd6RKoHzH9524fP4tsJh9uYVy3tteUfreA6qqN // · file // 📁 H8 // 📁 H8QZY941vLgEaeCpMBEf9LxiiexU7FF8YDZRTyLgJSo3 // · file\"\"\" // ) This store uses the id to create a directory using the first two characters and another below using the complete id. Then the data is stored in file and its attributes in attr. This can be changed by providing a different FsStoreConfig. The mapping of an id to a file in the filesystem is given by a PathMapping. There are some provided, the above results are from PathMapping.subdir2. As another example, the next FsBinaryStore puts the files directly into the baseDir - using the id as its name. val run2 = Stream.resource(DocUtil.tempDir).flatMap { baseDir =&gt; val store = FsBinaryStore[IO]( FsStoreConfig.default(baseDir).withMapping(PathMapping.simple), logger ) someData.through(store.insertWith(BinaryId(\"hello-world.txt\"))) ++ someData.through(store.insertWith(BinaryId(\"hello_world.txt\"))) ++ Stream.eval(DocUtil.directoryContentAsString(baseDir)) } // run2: Stream[[x]IO[x], String] = Stream(..) run2.compile.lastOrError.unsafeRunSync() // res1: String = \"\"\" // 📁 binny-docs-10763800237879449321 // · hello-world.txt // · hello_world.txt\"\"\" A PathMapping is a function (Path, BinaryId) =&gt; Path) where the given path is the base directory. So you can easily create a custom layout. FsChunkedBinaryStore The FsChunkedBinaryStore implements ChunkedBinaryStore to allow storing chunks independently. This is useful if chunks are received in random order and the whole file is not available as complete stream. This is implemented by storing each chunk as a file and concatenating these when loading. Therefore, a DirectoryMapping is required that maps a BinaryId to a directory (and not a file as PathMapping does). For binaries that are provided as a complete stream, it stores just one chunk file - same as FsBinaryStore does. However, in order to use this the complete size of the file must be known up front. This is needed to know when the last chunk is received. FsBinaryStoreWithCleanup The FsBinaryStoreWithCleanup is a wrapper around a FsBinaryStore that upon deletion of a file also removes all empty directories until its base path. Depending on which PathMapping is used, deleting a file could leave empty directories behind. The reason for this default behavior is so inserting and deleting are independent as they won’t write into same subdirectories. The FsBinaryStoreWithCleanup makes sure that inserting and deleting happen interleaved."
    } ,    
    {
      "title": "Implementation Modules",
      "url": "/binny/impls",
      "content": "Implementation Modules These modules provide implementations for BinaryStore and some also for BinaryAttributeStore. file system wrapping the fs2 Files API JDBC provides a generic implementation using pure JDBC PostgreSQL Large Object using PostgreSQLs Large Objects MinIO provides a S3 compatible store based on the SDK from MinIO"
    } ,    
    {
      "title": "Home",
      "url": "/binny/",
      "content": "Binny Binny is a Scala library for efficiently storing and retrieving (large) binary data from different storage systems, providing a unified, FS2 based api. It supports SQL databases via JDBC, PostgreSQLs large objects, S3 compatible object storage and the filesystem. Binny is provided for Scala 2.13 and 3. Usage With sbt, add the dependencies: \"com.github.eikek\" %% \"binny-core\" % \"0.9.0\" // the core library // … choose one or more implementation modules \"com.github.eikek\" %% \"binny-fs\" % \"0.9.0\" // implementation based on FS2 `Files` api \"com.github.eikek\" %% \"binny-jdbc\" % \"0.9.0\" // implementation based on JDBC \"com.github.eikek\" %% \"binny-pglo\" % \"0.9.0\" // implementation based on PostgreSQLs LargeObject API \"com.github.eikek\" %% \"binny-minio\" % \"0.9.0\" // implementation for MinIO \"com.github.eikek\" %% \"binny-tika-detect\" % \"0.9.0\" // Content-Type detection with Apache Tika License This project is distributed under the MIT The logo is from here."
    } ,    
    {
      "title": "JDBC",
      "url": "/binny/jdbc",
      "content": "Generic JDBC This module provides a BinaryStore using JDBC. It is tested for H2, PostgreSQL and MariaDB only, but other database systems probably work as well. The module doesn’t include any JDBC driver, you need to pull in the one you want to use in your project. The approach is as follows: the byte stream is split in chunks (size can be configured) and each chunk is stored in a blob column and associated to the same binary id. Table Structure Since the blob datatype characteristics differ between databases, you might want to define the table yourself or use the provided setup for PostgreSQL or MariaDB. See the CreateDataTable for the table definitions. Here is the definition for PostgreSQL: CREATE TABLE IF NOT EXISTS \"file_chunk\" ( \"file_id\" varchar(254) not null, \"chunk_nr\" int not null, \"chunk_len\" int not null, \"chunk_data\" bytea not null, primary key (\"file_id\", \"chunk_nr\") ) This makes range requests efficient, since it is possible to calculate at what chunk to start (and to end if applicable). The table names are just examples, they can be specified when creating a store. Chunksize when storing and streaming A caveat here is that the chunksize used to store a file, also determines the amount of memory used when reading the file. It is not possible to store in 512k chunks and then load it in 10k chunks, for example. The reason is that many jdbc drivers (at least these I know) don’t support streaming from a blob. You’ll get the whole blob in a byte array anyways. So this cannot be changed after a file has been stored. When streaming, the blob of each row is loaded in memory, one at a time. Its size defines the amount of memory used to stream a file. If you’re using PostgreSQL, consider the pglo module which doesn’t have this restriction. Usage You need to provide a javax.sql.DataSource. How to create this is out of scope for this project. A JdbcStoreConfig is required, that defines some settings, like the table name and chunk size to use. For the examples here, an in-memory database (H2) is used. import binny._ import binny.util.Logger import binny.jdbc._ import binny.ExampleData._ import cats.effect.IO import cats.effect.unsafe.implicits._ val dataSource = ConnectionConfig.h2Memory(\"docs\").dataSource // dataSource: javax.sql.DataSource = ds0: url=jdbc:h2:mem:docs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1 user=sa implicit val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@214738c1 val store = GenericJdbcStore.default[IO](dataSource, Logger.silent[IO]) // store: GenericJdbcStore[IO] = binny.jdbc.GenericJdbcStore$Impl@623dc959 // creates the schema, the table name is same as in the default config DatabaseSetup.runData[IO](Dbms.H2, dataSource, \"file_chunk\").unsafeRunSync() // res0: Int = 0 val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) val id = someData.through(store.insert) .compile.lastOrError.unsafeRunSync() // id: BinaryId = BinaryId(8kAic6mb33RzWps9LAtHceyuhv74qmTonjdsVZEDpvK3) // get the file out store.findBinary(id, ByteRange.All).getOrElse(sys.error(\"not found\")) .flatMap(binary =&gt; binary.readUtf8String) .unsafeRunSync() .take(50) // res1: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello wo\"\"\" JdbcBinaryStore As seen above, the store is an instance of the trait JdbcBinaryStore. It extends BinaryStore to add a findBinaryStateful method. The “state” relates to the connection to the database. The default findBinary method uses one connection per chunk. This allows to free resources each time the stream is pulled. Otherwise timeouts could occur, if for example the stream is not being pulled for a while. When a network client is consuming the stream with a slow connection, reading one chunk takes a while and could lead to the connection being closed (by the pool or server). However, if you know to process only small files or consume the data fast, it is possible to stream the whole file using a single connection, which is faster. This is provided by findBinaryStateful. ChunkedBinaryStore The GenericJdbcStore also implements ChunkedBinaryStore to allow storing chunks independently. This is useful if chunks are received in random order and the whole file is not available as complete stream. However, in order to use this the complete size of the file must be known up front. This is needed to know when the last chunk is received."
    } ,      
    {
      "title": "MinIO",
      "url": "/binny/minio",
      "content": "MinIO MiniO is a S3 compatible object storage which can be used to back a BinaryStore. It is available under a free license and can be self hosted. This module provides an implementation that is based on MinIOs Java SDK. The SDK provides an easy to use asynchronous http client. It is wrapped in an Async. Usage To create such a store, you only need the endpoint url, the credentials (an access- and secret key) and a mapping from a BinaryId to a pair of values: S3Key. The MinIO store expects its key to have a bucket and a filekey (or object id). Since a BinaryId is just one value, the store must know how to create the two values, bucket name and object id - which are combined in a S3Key. The example below starts a container running MinIO in order to work. The container knows the endpoint and keys and can create a config for creating the store. It’s only necessary to specify the mentioned mapping. Here we use a constant bucket name docs-bucket. import binny._ import binny.minio._ import binny.util.Logger import ExampleData._ import fs2.Stream import cats.effect.IO import cats.effect.unsafe.implicits._ val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@720f29f0 val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) // Create the `BinaryStore` val store = MinioBinaryStore[IO](DocUtil.minioConfig, logger) // store: MinioBinaryStore[IO] = binny.minio.MinioBinaryStore@7b3fd68e val run = for { // insert some data id &lt;- someData.through(store.insert) // get the file out bin &lt;- Stream.eval( store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error(\"not found\")) ) str &lt;- Stream.eval(bin.readUtf8String) } yield str + \"...\" // run: Stream[[x]IO[x], String] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello world 4 // hello world 5 // hello world 6 // hello world 7 // he...\"\"\" MinioChunkedBinaryStore The MinioChunkedBinaryStore implements ChunkedBinaryStore to allow uploading files in independent chunks. This is useful if chunks are received in random order and the whole file is not available as complete stream. For this the given S3KeyMapping is amended: the bucket is reused as is, but the object-name is appended with /chunk_00001 so the user given objectname is turned into a folder and each chunk is stored beneath. For binaries that are provided as a complete stream, it stores just one chunk file - similar to what MinioBinaryStore does. When a file is requested, all required chunks are loaded sequentially and concatenated."
    } ,    
    {
      "title": "PGLO",
      "url": "/binny/pglo",
      "content": "PostgreSQL Large Objects This module utilises PostgreSQLs Large Objects to implement a BinaryStore. This is then only for PostgreSQL, and it also depends on the postgresql jdbc driver. Using large objects, postgresql stores the data outside its standard table space and the object can be referenced by an id. They also allow to seek into a specific position, which is used to implement loading partial data. The PgLoBinaryStore also implements JdbcBinaryStore and provides two methods to retrieve a binary. One, the default findBinary, uses one connection per chunk. The other, findBinaryStateful uses a connection for the entire stream. Table Structure The table used here is: CREATE TABLE IF NOT EXISTS \"file_lo\" ( \"file_id\" varchar(254) NOT NULL PRIMARY KEY, \"data_oid\" oid NOT NULL ) Usage For the examples to run, a PostgreSQL server is necessary. It is quite easy to start one locally, for example with docker. import binny._ import binny.util.Logger import binny.ExampleData._ import binny.jdbc.ConnectionConfig import binny.pglo._ import fs2.Stream import cats.effect.IO import cats.effect.unsafe.implicits._ implicit val logger = Logger.silent[IO] // logger: Logger[IO] = binny.util.Logger$$anon$2@4a2eb2e val someData = ExampleData.file2M // someData: Binary[IO] = Stream(..) val ds = ConnectionConfig.Postgres.default.dataSource // ds: javax.sql.DataSource = org.postgresql.ds.PGSimpleDataSource@16ae647d val store = PgLoBinaryStore.default[IO](logger, ds) // store: PgLoBinaryStore[IO] = binny.pglo.PgLoBinaryStore@7843efc5 val run = for { // Create the schema _ &lt;- Stream.eval(PgSetup.run[IO](store.config.table, logger, ds)) // insert some data id &lt;- someData.through(store.insert) // get the file out bin &lt;- Stream.eval( store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error(\"not found\")) ) str &lt;- Stream.eval(bin.readUtf8String) } yield str + \"...\" // run: Stream[[x]IO[x], String] = Stream(..) run.compile.lastOrError.unsafeRunSync() // res0: String = \"\"\"hello world 1 // hello world 2 // hello world 3 // hello world 4 // hello world 5 // hello world 6 // hello world 7 // he...\"\"\" JdbcBinaryStore The PgLoBinaryStore also provides a findBinaryStateful variant just like the GenericJdbcStore. The default findBinary method creates a byte stream that loads the file in chunks. After every chunk, the connection is closed again and the next chunk seeks into the large object to start anew. In contrast, the findBinaryStateful method uses a single connection for the entire stream."
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
