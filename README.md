# truck-cache

Caches requests to endpoint service via `/geocode` endpoint.

## Usage

Run it with lein:

    $ REMOTE_URL=http://google.com lein run

## Options

Environment variable `REMOTE_URL` is used to specify url of remote endpoint. It can be supplied in any ways supported by [weavejester/environ](https://github.com/weavejester/environ).

## License
Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
