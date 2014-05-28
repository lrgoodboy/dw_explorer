define("/explorer/js/Runner.js", [
    "dojo",
    "dijit",
    "dijit/_editor/_Plugin",
    "dijit/form/Button"
], function(dojo, dijit, _Plugin, Button) {

    var Runner = dojo.declare("Runner", _Plugin, {

        _initButton: function() {
            var editor = this.editor,
                iconClass = '';//this.iconClassPrefix + " " + this.iconClassPrefix + "Runner";
            this.button = new Button({
                label: "Run Selected",
                showLabel: true,
                iconClass: iconClass,
                ownerDocument: editor.ownerDocument,
                dir: editor.dir,
                lang: editor.lang,
                tabIndex: '-1',
                onClick: dojo.hitch(this, "_runSelected")
            });
        },

        setEditor: function(editor) {
            this.editor = editor;
            this._initButton();
        },

        updateState: function() {
            this.button.set('disabled', this.get('disabled'));
        },

        _runSelected: function() {
            console.log("run selected");
        },

        _runAll: function() {
            console.log("run all");
        }

    });

    _Plugin.registry["runner"] = function(args) {
        return new Runner({});
    };

    return Runner;

});
