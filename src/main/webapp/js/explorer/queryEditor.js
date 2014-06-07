define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/query',
    'dojo/html',
    'dojo/date/locale',
    'dojo/request',
    'dojo/json',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dojo/domReady!'
], function(declare, lang, config, array, query, html, date, request, json, JsonRest, Observable, registry, ContentPane, OnDemandGrid, Selection, ColumnResizer) {

    var QueryEditor = declare(null, {

        constructor: function() {
            var self = this;
            self.initTaskStatus();
        },

        initTaskStatus: function() {
            var self = this;

            // store
            self.taskStore = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/task/'
            }));

            // grid
            self.grid = new (declare([OnDemandGrid, Selection, ColumnResizer]))({
              className: 'dgrid-autoheight',
                sort: [{attribute: 'created', descending: true}],
                store: self.taskStore,
                columns: [
                    {label: '提交时间', field: 'created'},
                    {label: '查询语句', field: 'queries'},
                    {label: '状态', field: 'status'},
                    {label: '运行时间', field: 'duration'}
                ],
                selectionMode: 'single'
            }, 'gridTaskStatus');

            var updated = null;

            setInterval(function() {

                if (updated == null) {
                    query('.dgrid-row', self.grid.domNode).forEach(function(node) {
                        var task = self.grid.row(node).data;
                        if (updated == null || task.updated > updated) {
                            updated = task.updated;
                        }
                    });
                }

                var data = {};
                if (updated != null) {
                    data['updated'] = updated;
                }

                self.taskStore.query(data).forEach(function(task) {

                    self.taskStore.notify(task, task.id);

                    if (updated == null || task.updated > updated) {
                        updated = task.updated;
                    }

                    // show result if succeeded
                    if (task.status == '运行成功') {
                        request(config.contextPath + '/query-editor/api/task/output/' + task.id).then(function(data) {
                            var pane = new ContentPane({
                                title: '查询结果[' + task.id + ']',
                                content: data
                            });
                            registry.byId('bottomCol').addChild(pane);
                        });
                    }

                });

            }, 3000);

        },

        submitTask: function(queries) {
            var self = this;

            queries = lang.trim(queries);
            if (!queries) {
                alert('Queries cannot be empty.');
                return false;
            }

            self.taskStore.add({
                queries: queries
            });

        }

    });

    return new QueryEditor();

});
