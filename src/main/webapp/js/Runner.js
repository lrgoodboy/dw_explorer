define('explorer/Runner', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/array',
    'dijit/_editor/_Plugin',
    'dijit/form/Button'
], function(declare, lang, array, _Plugin, Button) {

    var Runner = declare('explorer.Runner', _Plugin, {

        buttonGroup: [],

        _initButtonGroup: function() {
            var editor = this.editor;

            var props = {
                showLabel: true,
                iconClass: '',
                ownerDocument: editor.ownerDocument,
                dir: editor.dir,
                lang: editor.lang,
                tabIndex: '-1'
            };

            this.buttonGroup.push(new Button(lang.mixin(props, {
                label: 'Run Selected',
                onClick: lang.hitch(this, '_runSelected')
            })));

            this.buttonGroup.push(new Button(lang.mixin(props, {
                label: 'Run All',
                onClick: lang.hitch(this, '_runAll')
            })));

        },

        setEditor: function(editor) {
            this.editor = editor;
            this._initButtonGroup();
        },

        setToolbar: function(toolbar) {
            array.forEach(this.buttonGroup, function(button) {
                toolbar.addChild(button);
            });
        },

        updateState: function() {
            var runner = this;
            array.forEach(this.buttonGroup, function(button) {
                button.set('disabled', runner.get('disabled'));
            });
        },

        _runSelected: function() {
            console.log('run selected');
        },

        _runAll: function() {
            console.log('run all');
        }

    });

    _Plugin.registry['runner'] = function(args) {
        return new Runner();
    };

    return Runner;

});
