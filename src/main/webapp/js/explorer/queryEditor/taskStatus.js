define('explorer/queryEditor/taskStatus', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/ready',
    'dojo/request',
    'dojo/json',
    'dojo/cookie',
    'dojo/store/Memory',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/Menu',
    'dijit/MenuItem',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'put-selector/put'
], function(declare, lang, config, array, ready, request, json, cookie, Memory, JsonRest, Observable,
            registry, ContentPane, Menu, MenuItem,
            Grid, OnDemandGrid, Selection, ColumnResizer,
            put) {

    var TaskStatus = declare(null, {

        constructor: function() {
            var self = this;
            ready(function() {
                self.initGrid();
                self.initWebSocket();
            });
        },

        initGrid: function() {
            var self = this;

            // store
            self.taskStore = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/task/'
            }));

            // grid
            var CustomGrid = declare([OnDemandGrid, Selection]);
            self.grid = new CustomGrid({
                className: 'grid-task-status grid-fill-container',
                sort: [{attribute: 'id', descending: true}],
                store: self.taskStore,
                columns: [
                    {label: 'ID', field: 'id', sortable: false},
                    {label: '查询语句', field: 'queriesBrief', sortable: false},
                    {label: '创建时间', field: 'created', sortable: false},
                    {label: '状态', field: 'status', sortable: false},
                    {label: '运行时间', field: 'duration', sortable: false}
                ],
                selectionMode: 'single',
                renderRow: function(object, options) {
                    var div = put('div.collapsed', CustomGrid.prototype.renderRow.apply(this, arguments)),
                        expando = put(div, 'div.expando', {innerHTML: object.queries.replace(/\n/g, '<br>')});
                    return div;
                }
            }, 'gridTaskStatus');

            // https://github.com/SitePen/dgrid/blob/v0.3.15/demos/multiview/multiview.js
            var expandedNode;
            self.grid.on('.dgrid-row:click', function(evt) {
                var node = self.grid.row(evt).element,
                    collapsed = node.className.indexOf('collapsed') >= 0;
                put(node, (collapsed ? '!' : '.') + 'collapsed');
                collapsed && expandedNode && put(expandedNode, '.collapsed');
                expandedNode = collapsed ? node : null;
            });

            // context menu
            function getSelectedTask() {
                var task = null;
                for (var id in self.grid.selection) {
                    if (self.grid.selection[id]) {
                        task = self.grid.row(id).data;
                        break;
                    }
                }
                return task;
            }

            var menu = new Menu({
                targetNodeIds: [self.grid.domNode]
            });
            menu.addChild(new MenuItem({
                label: '查看结果',
                onClick: function() {
                    self.showResult(getSelectedTask());
                }
            }));
            menu.addChild(new MenuItem({
                label: '取消任务',
                onClick: function() {
                    var task = getSelectedTask();
                    request(config.contextPath + '/query-editor/api/task/cancel/' + task.id);
                }
            }));
            /*menu.addChild(new MenuItem({
                label: '删除任务'
            }));*/

        },

        showResult: function(task) {
            var self = this;

            var showError;

            switch (task.status) {
            case '运行成功':
                showError = false;
                break;
            case '运行失败':
                showError = true;
                break;
            default:
                return;
            }

            var pane = new ContentPane({
                title: '运行结果[' + task.id + ']',
                closable: true
            });

            /*var gridStatus = new Grid({
                className: 'dgrid-autoheight grid-task-status',
                columns: [
                    {label: 'ID', field: 'id', sortable: false},
                    {label: '查询语句', field: 'queriesBrief', sortable: false},
                    {label: '创建时间', field: 'created', sortable: false},
                    {label: '状态', field: 'status', sortable: false},
                    {label: '运行时间', field: 'duration', sortable: false}
                ],
                renderRow: function(object, options) {
                    var div = put('div.collapsed', Grid.prototype.renderRow.apply(this, arguments)),
                        expando = put(div, 'div.expando', {innerHTML: object.queries.replace(/\n/g, '<br>')});
                    return div;
                }
            });

            gridStatus.on('.dgrid-row:click', function(evt) {
                var node = gridStatus.row(evt).element,
                    collapsed = node.className.indexOf('collapsed') >= 0;
                put(node, (collapsed ? '!' : '.') + 'collapsed');
            });

            gridStatus.renderArray([task]);

            pane.addChild(gridStatus);*/

            var bottomCol = registry.byId('bottomCol');
            bottomCol.addChild(pane);
            bottomCol.selectChild(pane);

            if (showError) {

                request(config.contextPath + '/query-editor/api/task/error/' + task.id).then(function(data) {
                    put(pane.domNode, 'div.task-result-header', '错误日志');
                    put(pane.domNode, 'pre', data);
                });

            } else {

                request(config.contextPath + '/query-editor/api/task/output/' + task.id, {
                    handleAs: 'json'
                }).then(function(result) {

                    if (result.columns.length == 0) {
                        put(pane.domNode, 'div.task-result-header', '未返回结果');
                        return;
                    }

                    /*if (result.rows.length == 0) {
                        put(pane.domNode, 'div.task-result-header', '返回结果为空');
                    } else {
                        var div = put(pane.domNode, 'div.task-result-header', '结果列表（前100条）');
                        put(div, 'a[href="' + config.contextPath + '/query-editor/api/task/excel/' + task.id + '"][target="_blank"]', '下载Excel');
                    }*/

                    var gridOutput = new (declare([OnDemandGrid, ColumnResizer]))({
                        columns: result.columns,
                        className: 'grid-fill-container'
                    });

                    if (result.hasMore) {
                        var more = {};
                        more[result.columns[0].label] = '...';
                        console.log(more);
                        result.rows.push(more);
                    }

                    gridOutput.renderArray(result.rows);

                    pane.addChild(gridOutput);
                });

            }
        },

        submitTask: function(queries) {
            var self = this;

            queries = lang.trim(queries);

            // check empty
            var sqls = queries.replace(/\/\*[\s\S]*?\*\//g, '').split(/;/);
            sqls = array.map(sqls, function(item) {
                return lang.trim(item);
            });
            sqls = array.filter(sqls, function(item) {
                return !!item;
            });

            var ptrnBuffer = /^(SET|ADD\s+JAR|CREATE\s+TEMPORARY\s+FUNCTION|USE)\s+/i;
            var isEmpty = !array.some(sqls, function(sql) {
                return !ptrnBuffer.test(sql);
            });

            if (isEmpty) {
                alert('查询语句不能为空。');
                return;
            }

            // submit task
            self.taskStore.add({
                queries: queries
            });

            // switch to task status pane
            registry.byId('bottomCol').selectChild('paneTaskStatus');
        },

        initWebSocket: function() {
            var self = this;

            if (!WebSocket) {
                alert('您的浏览器不支持WebSocket，请更换。');
                return;
            }

            var socket = new WebSocket('ws://' + config.websocketServer + '/explorer/query-task/list');

            socket.onopen = function(event) {
                console.log('WebSocket connected, subscribing to remote events.');
                socket.send(json.stringify({
                    action: 'subscribe',
                    token: cookie(config.cookieKey)
                }));
            };

            socket.onmessage = function(event) {

                var message = json.parse(event.data);

                if (message.status != 'ok') {
                    console.log('Something went wrong: ' + message.msg);
                    return;
                }

                if ('subscribe' in message) {
                    console.log('Subscribed to user id: ' + message.subscribe);
                    return;
                }

                if ('task' in message) {
                    console.log('Receive task event, id: ' + message.task.id);
                    self.taskStore.notify(message.task, message.task.id);
                    self.showResult(message.task);
                    return;
                }

                console.log('Unknown message: ' + json.stringify(message));
            };

            socket.onclose = function(event) {
                console.log('Connection is closed, reconnect in 3 seconds.');
                setTimeout(function() {
                    self.initWebSocket();
                }, 3000);
            };
        },

        _theEnd: undefined
    });

    return new TaskStatus();
});
