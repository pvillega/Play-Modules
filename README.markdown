Play Modules
=====================================

This application allows the management of [Play Framework](http://www.playframework.org/) modules and demos.

As there is no page to manage them properly since 2.0 was released and I needed some project to learn Play 2.0, I though on creating this.
It's simple, not specially pretty, and it may have some bugs. But it kind of works :)

See the project live at [http://www.playmodules.net/](http://www.playmodules.net/)

Feel free to use the code for your own aims. And if you can, please contribute back! All feedback is welcome :)


Features
------------------
- Bootstrap based (and *should* be responsive)
- User authentication via Github, Twitter or Google accounts
- Profile management
- Publication of your own Demos and Modules
- Voting on Demos/Modules
- Public search of Demos and Modules
- [Pjax](https://github.com/defunkt/jquery-pjax) for faster rendering on GET requests (not used on POST requests!)

Deployment
------------------
The project is ready for deployment in Heroku, although it can be deployed in any environment given it uses PostgreSQL 9.1 (or newer) as database.

You will need to set the following global variables with the corresponding keys:
+ github.clientId : id of your Github application, to enable Github authentication
+ github.secret : secret of your Github application, to enable Github authentication
+ twitter.clientId : id of your Twitter application, to enable Twitter authentication
+ twitter.secret : secret of your Twitter application, to enable Twitter authentication
+ disqus.forum  : id of your Disqus forum, to enable comments
+ google.analytics : Google Analytics code

Once deployed, there will be no users. Log into the application to create your user automatically.

Then you will need to access the database to give yourself admin rights by enabling the 'admin' flag:
$ heroku pg:psql
>> update publisher set admin = true where id = <yourId>

TO DO
------------------
- Update to Play 2.1 when released
- Add tests (currently due to the lack of support for session values in FakeRequest this can't be really done. This should be fixed in 2.1)
- Improve with community suggestions


License
------------------

Copyright (c) 2012 Pere Villega

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
