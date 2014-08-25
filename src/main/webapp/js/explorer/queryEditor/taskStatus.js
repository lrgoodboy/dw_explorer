define('explorer/queryEditor/taskStatus', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/ready',
    'dojo/request',
    'dojo/json',
    'dojo/cookie',
    'dojo/query',
    'dojo/html',
    'dojo/store/Memory',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/Menu',
    'dijit/MenuItem',
    'dijit/Dialog',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'put-selector/put',
    'zc/ZeroClipboard'
], function(declare, lang, config, array, ready, request, json, cookie, query, html, Memory, JsonRest, Observable,
            registry, ContentPane, Menu, MenuItem, Dialog,
            Grid, OnDemandGrid, Selection, ColumnResizer,
            put, ZeroClipboard) {

    var TaskStatus = declare(null, {

        constructor: function() {
            var self = this;
            ready(function() {
                self.initGrid();
                self.initWebSocket();
            });
        },

        createTaskStatusGrid: function(CustomGrid, options) {
            var self = this;

            var grid = new CustomGrid(lang.mixin({
                className: 'grid-task-status',
                columns: [
                    {label: 'ID', field: 'id', sortable: false},
                    {label: '查询语句', field: 'queriesBrief', sortable: false},
                    {label: '创建时间', field: 'created', sortable: false},
                    {label: '状态', field: 'status', sortable: false},
                    {label: '运行时间', field: 'duration', sortable: false}
                ],
                renderRow: function(object, options) {
                    var div = put('div.collapsed', CustomGrid.prototype.renderRow.apply(this, arguments)),
                        expando = put(div, 'div.expando', {innerHTML: object.queries.replace(/\n/g, '<br>')});
                    return div;
                }
            }, options));

            // https://github.com/SitePen/dgrid/blob/v0.3.15/demos/multiview/multiview.js
            var expandedNode;
            grid.on('.dgrid-row:click', function(evt) {
                var node = grid.row(evt).element,
                    collapsed = node.className.indexOf('collapsed') >= 0;
                put(node, (collapsed ? '!' : '.') + 'collapsed');
                collapsed && expandedNode && put(expandedNode, '.collapsed');
                expandedNode = collapsed ? node : null;
            });

            return grid;
        },

        initGrid: function() {
            var self = this;

            // store
            self.taskStore = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/task/'
            }));

            // grid
            self.grid = self.createTaskStatusGrid(declare([OnDemandGrid, Selection]), {
                className: 'grid-task-status grid-fill-container',
                sort: [{attribute: 'id', descending: true}],
                store: self.taskStore,
                selectionMode: 'single'
            });
            registry.byId('paneTaskStatus').addChild(self.grid);

            // context menu
            function getSelectedTask() {
                for (var id in self.grid.selection) {
                    if (self.grid.selection.hasOwnProperty(id)) {
                        return self.grid.row(id).data;
                    }
                }
                return null;
            }

            var menu = new Menu({
                targetNodeIds: [self.grid.domNode]
            });
            menu.addChild(new MenuItem({
                label: '查看结果',
                onClick: function() {
                    var task = getSelectedTask();
                    if (task) {
                        self.showResult(task);
                    }
                }
            }));
            menu.addChild(new MenuItem({
                label: '取消任务',
                onClick: function() {
                    var task = getSelectedTask();
                    if (task) {
                        request(config.contextPath + '/query-editor/api/task/cancel/' + task.id);
                    }
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

            var bottomCol = registry.byId('bottomCol');
            bottomCol.addChild(pane);
            bottomCol.selectChild(pane);

            if (showError) {

                var gridStatus = self.createTaskStatusGrid(Grid, {
                    className: 'grid-task-status dgrid-autoheight'
                });
                pane.addChild(gridStatus);
                gridStatus.renderArray([task]);

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

                    var gridOutput = new (declare([OnDemandGrid, ColumnResizer]))({
                        columns: result.columns,
                        className: 'grid-fill-container'
                    });
                    pane.addChild(gridOutput);

                    // rows
                    var limit = 100;
                    var rows = result.rows;
                    if (rows.length > limit) {
                        rows = rows.slice(0, limit);
                        var more = {};
                        more[result.columns[0].label] = '...';
                        rows.push(more);
                    }

                    gridOutput.renderArray(rows);

                    // context menu
                    var menu = new Menu({
                        targetNodeIds: [gridOutput.domNode]
                    });
                    menu.addChild(new MenuItem({
                        label: '任务信息',
                        onClick: function() {
                            var dlg = registry.byId('dlgTaskInfo');
                            dlg.set('title', '任务信息[' + task.id + ']');

                            var tds = query('td', dlg.domNode);
                            html.set(tds[0], task.created);
                            html.set(tds[1], task.duration);
                            html.set(tds[2], task.queries.replace(/\n/g, '<br>'));

                            dlg.show();
                        }
                    }));
                    menu.addChild(new MenuItem({
                        label: '新窗口打开',
                        onClick: function() {
                            var url = config.contextPath + '/query-editor/task/result/' + task.id;
                            open(url);
                        }
                    }));
                    var clipboardMenu = new MenuItem({
                        label: '复制到剪贴板'
                    });
                    menu.addChild(clipboardMenu);
                    menu.addChild(new MenuItem({
                        label: '下载Excel',
                        onClick: function() {
                            var url = config.contextPath + '/query-editor/api/task/excel/' + task.id;
                            open(url);
                        }
                    }));

                    var clipboard = new ZeroClipboard(clipboardMenu.domNode);
                    clipboard.on('copy', function(event) {

                        var lines = [];

                        var columns = array.map(result.columns, function(column) {
                            return column.label;
                        }).join('\t');
                        lines.push(columns);

                        array.forEach(result.rows, function(row) {
                            lines.push(array.map(result.columns, function(column) {
                                return row[column.label];
                            }).join('\t'));
                        });

                        event.clipboardData.setData('text/plain', lines.join('\n'));

                        if (result.hasMore) {
                            self.showToaster('已复制前1000行，更多数据请下载Excel。')
                        } else {
                            self.showToaster('查询结果已全部复制到剪贴板。');
                        }
                    });

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

        showToaster: function(content) {
            var toaster = registry.byId('toaster');
            toaster.setContent(content);
            toaster.show();
        },

        _theEnd: undefined
    });

    return new TaskStatus();
});
