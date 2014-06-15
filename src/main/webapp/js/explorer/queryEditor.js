define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/query',
    'dojo/html',
    'dojo/dom-style',
    'dojo/on',
    'dojo/date/locale',
    'dojo/request',
    'dojo/json',
    'dojo/store/Memory',
    'dojo/store/JsonRest',
    'dojo/store/Observable',
    'dojo/store/util/QueryResults',
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/Tree',
    'dijit/tree/ObjectStoreModel',
    'dijit/Editor',
    'dijit/Menu',
    'dijit/MenuItem',
    'dijit/form/Select',
    'dijit/form/TextBox',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dgrid/util/misc',
    'dojox/editor/plugins/Save',
    'put-selector/put',
    'dojo/domReady!'
], function(declare, lang, config, array, query, html, domStyle, on, date, request, json, Memory, JsonRest, Observable, QueryResults,
            registry, ContentPane, Tree, ObjectStoreModel, Editor, Menu, MenuItem, Select, TextBox,
            Grid, OnDemandGrid, Selection, ColumnResizer, dgridUtil, EditorSavePlugin, put) {

    var QueryEditor = declare(null, {

        constructor: function() {
            var self = this;
            self.initTaskStatus();
            self.initDocument();
            self.initMetadata();
        },

        initTaskStatus: function() {
            var self = this;

            // store
            self.taskStore = Observable(JsonRest({
                target: config.contextPath + '/query-editor/api/task/'
            }));

            // grid
            var CustomGrid = declare([OnDemandGrid, Selection]);
            self.grid = new CustomGrid({
                className: 'dgrid-autoheight grid-task-status',
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
                label: '取消任务'
            }));
            menu.addChild(new MenuItem({
                label: '删除任务'
            }));

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

                    self.showResult(task);
                });

            }, 3000);

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

            var gridStatus = new Grid({
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

            pane.addChild(gridStatus);

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

                    if (result.rows.length == 0) {
                        put(pane.domNode, 'div.task-result-header', '返回结果为空');
                    } else {
                        var div = put(pane.domNode, 'div.task-result-header', '结果列表（前100条）');
                        put(div, 'a[href="' + config.contextPath + '/query-editor/api/task/excel/' + task.id + '"]', '下载Excel');
                    }

                    var gridOutput = new (declare([OnDemandGrid, ColumnResizer]))({
                        store: new Memory({data: result.rows}),
                        columns: result.columns,
                        className: 'dgrid-autoheight grid-task-result'
                    });

                    pane.addChild(gridOutput);
                });

            }
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

        },

        initDocument: function() {
            var self = this;

            var rest = JsonRest({
                target: config.contextPath + '/query-editor/api/doc/',
            });

            rest.query().then(function(docs) {

                var store = Observable(Memory({
                    data: docs,
                    getChildren: function(object) {
                        return this.query({parent: object.id}, {
                            sort: [
                                {attribute: 'isFolder', descending: true},
                                {attribute: 'name', descending: false}
                            ]
                        });
                    }
                }));

                var model = new ObjectStoreModel({
                    store: store,
                    query: {id: 0},
                    mayHaveChildren: function(item) {
                        return item.isFolder;
                    },
                });

                self.treeDoc = new Tree({
                    model: model,
                    onDblClick: function(item) {

                        if (item.isFolder) {
                            return;
                        }

                        var central = registry.byId('central');
                        var paneId = 'editorPane_' + item.id;
                        var pane = registry.byId(paneId);

                        if (typeof pane != 'undefined') {
                            central.selectChild(pane);
                            return;
                        }

                        rest.get(item.id).then(function(object) {

                            var pane = new ContentPane({
                                id: paneId,
                                title: object.name,
                                closable: true
                            });

                            var docUrl = config.contextPath + '/query-editor/api/doc/content/' + item.id;

                            var editor = new Editor({
                                plugins: ['undo', 'redo', '|', {name: 'save', url: docUrl}, '|', 'runner'],
                                value: object.content
                            });

                            pane.addChild(editor);
                            central.addChild(pane);
                            central.selectChild(pane);
                        });
                    }
                }, 'treeDoc');

                // context menu
                var menu = new Menu({
                    targetNodeIds: [self.treeDoc.domNode],
                    selector: '.dijitTreeNode'
                });

                menu.addChild(new MenuItem({
                    label: '新建文件',
                    onClick: function() {

                        var name = prompt('请输入文件名');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.add({
                            parent: item.isFolder ? item.id : item.parent,
                            filename: name,
                            isFolder: false
                        }).then(function(doc) {
                            store.notify(doc);
                        });
                    }
                }));

                menu.addChild(new MenuItem({
                    label: '新建文件夹',
                    onClick: function() {

                        var name = prompt('请输入文件夹名称');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.add({
                            parent: item.isFolder ? item.id : item.parent,
                            filename: name,
                            isFolder: true
                        }).then(function(doc) {
                            store.notify(doc);
                        });

                    }
                }));

                menu.addChild(new MenuItem({
                    label: '重命名',
                    onClick: function() {
                        var name = prompt('请输入新的名称');
                        if (!name) {
                            return;
                        }

                        var item = registry.byNode(this.getParent().currentTarget).item;

                        rest.put({
                            id: item.id,
                            filename: name
                        }).then(function(doc) {
                            store.notify(doc, doc.id);
                        });

                    }
                }));

                menu.addChild(new MenuItem({
                    label: '删除',
                    onClick: function() {
                        if (!confirm('确定要删除吗？')) {
                            return;
                        }
                        var item = registry.byNode(this.getParent().currentTarget).item;
                        rest.remove(item.id);
                        store.notify(null, item.id);
                    }
                }));


                self.treeDoc.startup();
            });
        },

        initMetadata: function() {
            var self = this;

            domStyle.set('loadingTables', 'display', 'none');

            var cache = {};

            var store = new JsonRest({
                target: config.contextPath + '/query-editor/api/metadata/'
            });

            store.query().then(function(databases) {

                var options = [{label: '请选择数据库', value: '', selected: true}];
                array.forEach(databases, function(database) {
                    options.push({label: database.id, value: database.name});
                });

                var select = new Select({
                    options: options,
                    autoWidth: true,
                    style: {width: '100%'}
                }, 'selDatabase');

                select.on('change', function() {

                    domStyle.set('divTables', 'display', 'none');

                    var database = this.get('value');
                    if (!database) {
                        return;
                    }

                    var table = this.get('value');

                    if (!(table in cache)) {
                        domStyle.set('loadingTables', 'display', '');
                        store.query({database: database}).then(function(tables) {
                            cache[database] = tables;
                            domStyle.set('loadingTables', 'display', 'none');
                            domStyle.set('divTables', 'display', '');
                        });
                    } else {
                        domStyle.set('divTables', 'display', '');
                    }

                });

                select.startup();
            });

            // search box
            var txtTable = new TextBox({
                placeHolder: '请输入表名关键字',
                trim: true,
                style: {width: '100%'}
            }, 'txtTable');

            var searchTable = dgridUtil.debounce(function() {

                var database = registry.byId('selDatabase').get('value');
                var table = registry.byId('txtTable').get('value');

                var MAX = 20;
                var result = [];

                if (!table) {
                    for (var i = 0; i < MAX && i < cache[database].length; ++i) {
                        result.push(cache[database][i]);
                    }
                } else {

                    array.every(cache[database], function(object) {
                        if (object.name.indexOf(table) !== -1) {
                            result.push(object);
                        }
                        return result.length < MAX;
                    });

                }

                var ulTables = query('#ulTables')[0];
                query('li', ulTables).forEach(function(li) {
                    put(li, '!');
                });

                if (result.length == 0) {
                    put(ulTables, 'li', '查无结果');
                } else {
                    array.forEach(result, function(object) {
                        var li = put(ulTables, 'li');
                        var anchor =  put(li, 'a[href="javascript:void(0);"]', object.name);
                        on(anchor, 'click', function() {
                            console.log(object);
                        });
                    });
                }

            }, null, 500);

            txtTable.on('input', function(evt) {
                searchTable();
            });

            txtTable.startup();
        },

        _theEnd: undefined

    });

    return new QueryEditor();

});
