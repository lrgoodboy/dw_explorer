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
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dojo/domReady!'
], function(declare, lang, config, array, query, html, date, request, json, JsonRest, Observable, OnDemandGrid, Selection, ColumnResizer) {

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
            var CustomGrid = declare([OnDemandGrid, Selection, ColumnResizer]);
            var grid = new CustomGrid({
                sort: [{attribute: 'created', descending: true}],
                store: this.taskStore,
                columns: [
                    {label: '提交时间', field: 'created'},
                    {label: '查询语句', field: 'queries'},
                    {label: '状态', field: 'status'},
                    {label: '运行时间', field: 'duration'}
                ],
                selectionMode: 'single'
            }, 'gridTaskStatus');

            setInterval(function() {
                self.taskStore.query({status: [1, 2]}).then(function(tasks) {
                    array.forEach(tasks, function(task) {
                        self.taskStore.notify(task, task.id);
                    }) ;
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
