.PHONY: clean sass build font replace-time
sass:
	npm run build:css
clean:
	lein clean
build:  sass
	lein fig:min
