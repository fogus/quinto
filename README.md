# Quinto

An implementation of an old abandoned board game.

![quinto board game box][box-image]

* [Play the game][game]
* Read the blog post: [Quinto: Resurrecting an Abandoned Board Game][post]

## Develoipment

Build:

    clojure -m figwheel.main --optimizations whitespace  --build-once quinto

Then serve the resources/public directory:

    cd resources/public
    python3 -m http.server 8000

Open http://localhost:8000/index.html in your browser.

Build and run a live-REPL (not currently working):

    clojure -m figwheel.main --build quinto --repl
	

[post]: http://blog.jrheard.com/quinto-resurrecting-an-abandoned-board-game
[game]: http://blog.jrheard.com/quinto
[box-image]: http://blog.jrheard.com/assets/quinto_box-59e0eb60c5c81325c53ed0d7ee3d3e1fc756b62d0b9cdba63fdd6b0c4fb310ae.jpg

