define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/request',
    'dojo/json',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dojo/domReady!'
], function(declare, lang, config, array, request, json, JsonRest, Observable, OnDemandGrid, Selection, ColumnResizer) {

    var QueryEditor = declare(null, {

        constructor: function() {

            this.initTaskStatus();

        },

        initTaskStatus: function() {

            // store
            this.taskStore = Observable(JsonRest({
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

            var taskStore = this.taskStore;
            setTimeout(function() {
                taskStore.query({status: 1}).then(function(tasks) {
                    array.forEach(tasks, function(task) {
                        taskStore.notify(task, task.id);
                    }) ;
                });
            }, 3000);

        },

        submitTask: function(queries) {

            queries = lang.trim(queries);
            if (!queries) {
                alert('Queries cannot be empty.');
                return false;
            }

            this.taskStore.add({
                queries: queries
            });

        }

    });

    return new QueryEditor();

});
