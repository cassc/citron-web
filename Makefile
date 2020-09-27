.PHONY: clean sass build font replace-time
sync-file:
	rsync -avhc --delete resources/public/ pi-xs:/var/www/public/pic-web/
sass:
	npm run build:css
clean:
	lein clean
build:  sass
	lein fig:min
replace-time:
	python script/replace-time.py
recover-time:
	python script/recover-time.py
bump-release:
	lein release bump-release-version
bump-dev:
	lein release bump-dev-version
deploy: bump-release clean build replace-time sync-file recover-time bump-dev
