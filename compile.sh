CP=classes/:lib/*
SP=src/

/bin/mkdir -p classes/

javac -sourcepath $SP -classpath $CP -d classes/ src/poc2plotter/*.java src/nxt/*/*.java || exit 1

/bin/rm poc2plotter.jar
jar cf poc2plotter.jar -C classes . || exit 1
/bin/rm -rf classes

echo "poc2plotter.jar generated successfully"

