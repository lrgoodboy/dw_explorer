define('explorer/queryEditor', [
    'dojo/_base/declare',
    'dojo/_base/lang',
    'dojo/_base/config',
    'dojo/_base/array',
    'dojo/ready',
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
    'dijit/registry',
    'dijit/layout/ContentPane',
    'dijit/layout/LayoutContainer',
    'dijit/Tree',
    'dijit/tree/ObjectStoreModel',
    'dijit/Menu',
    'dijit/MenuItem',
    'dijit/form/Select',
    'dijit/form/TextBox',
    'dijit/form/Button',
    'dijit/form/CheckBox',
    'dijit/Toolbar',
    'dijit/Fieldset',
    'dgrid/Grid',
    'dgrid/OnDemandGrid',
    'dgrid/Selection',
    'dgrid/extensions/ColumnResizer',
    'dgrid/util/misc',
    'put-selector/put',
    'cm/lib/codemirror',
    'cm/mode/sql/sql',
    'explorer/queryEditor/taskStatus'
], function(declare, lang, config, array, ready, query, html, domStyle, on, date, request, json, Memory, JsonRest, Observable,
            registry, ContentPane, LayoutContainer, Tree, ObjectStoreModel, Menu, MenuItem, Select, TextBox, Button, CheckBox, Toolbar, Fieldset,
            Grid, OnDemandGrid, Selection, ColumnResizer, dgridUtil, put, CodeMirror, cmdModeSql, taskStatus) {

    var QueryEditor = declare(null, {

        constructor: function() {
            var self = this;
            ready(function() {
                self.initDocument();
                self.initMetadata();
                self.initOption();
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

                            // http://dojotoolkit.org/reference-guide/1.10/dijit/layout.html
                            // layout
                            var pane = new ContentPane({
                                id: paneId,
                                title: object.name,
                                closable: true
                            });

                            var layout = new LayoutContainer({
                                style: 'width: 100%; height: 100%; border: 1px solid silver;'
                            });

                            var toolbarPane = new ContentPane({
                                region: 'top',
                                style: 'padding: 0;'
                            });

                            var toolbar = new Toolbar();

                            var btnSave = new Button({
                                label: 'Save'
                            });
                            toolbar.addChild(btnSave);

                            var btnRunSelected = new Button({
                                label: 'Run Selected'
                            });
                            toolbar.addChild(btnRunSelected);

                            var btnRunAll = new Button({
                                label: 'Run All'
                            });
                            toolbar.addChild(btnRunAll);

                            toolbarPane.addChild(toolbar);
                            layout.addChild(toolbarPane);

                            var editorPane = new ContentPane({
                                region: 'center',
                                style: 'height: 100%; padding: 0;'
                            });

                            layout.addChild(editorPane);
                            pane.addChild(layout);

                            central.addChild(pane);
                            central.selectChild(pane);

                            // editor
                            var editor = CodeMirror(editorPane.domNode, {
                                value: object.content,
                                mode: 'text/x-hive'
                            });

                            pane.codeMirror = editor;

                            btnSave.on('click', function() {

                                rest.put({
                                    id: item.id,
                                    content: editor.getValue()
                                });

                            });

                            btnRunSelected.on('click', function() {
                                taskStatus.submitTask(editor.getSelection());
                            });

                            btnRunAll.on('click', function() {
                                taskStatus.submitTask(editor.getValue());
                            });

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
                            txtTable.set('value', '');
                            searchTable();
                        });
                    } else {
                        domStyle.set('divTables', 'display', '');
                        txtTable.set('value', '');
                        searchTable();
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
                            var segs = object.id.split(/\./);
                            self.descTable(segs[0], segs[1]);
                        });
                    });
                }

            }, null, 500);

            txtTable.on('input', function(evt) {
                searchTable();
            });

            txtTable.startup();
        },

        descTable: function(database, table) {
            var self = this;

            var pane = new ContentPane({
                title: '表信息[' + table + ']',
                closable: true
            });

            var bottomCol = registry.byId('bottomCol');
            bottomCol.addChild(pane);
            bottomCol.selectChild(pane);

            var loading = put(pane.domNode, 'div[style="width: 100%; margin: 10px;"]');
            put(loading, 'img[src="' + config.contextPath + '/webjars/dojo/1.9.3/dijit/themes/claro/images/loadingAnimation.gif"][align="top"]');
            put(loading, 'span', '加载中……');

            request(config.contextPath + '/query-editor/api/metadata/desc/', {
                query: {database: database, table: table},
                handleAs: 'json'
            }).then(function(result) {

                put(loading, '!');

                if (result.columns.length == 0) {
                    put(pane.domNode, 'div[style="color: red;"]', '加载失败');
                    return;
                }

                var infoGrid  = new Grid({
                    className: 'dgrid-autoheight',
                    columns: [
                        {label: '数据库', field: 'database', sortable: false},
                        {label: '表名', field: 'table', sortable: false},
                        {label: '分区字段', field: 'partitions', sortable: false},
                        {label: '整表大小', field: 'size', sortable: false}
                    ]
                });

                infoGrid.renderArray(result.info);
                pane.addChild(infoGrid);

                var columnGrid = new Grid({
                    className: 'dgrid-autoheight',
                    columns: [
                        {label: '字段名', field: 'name', sortable: false},
                        {label: '类型', field: 'type', sortable: false},
                        {label: '注释', field: 'comment', sortable: false}
                    ]
                });

                columnGrid.renderArray(result.columns);
                pane.addChild(columnGrid);

                put(pane.domNode, 'div.task-result-header', '样本数据');

                var rowGrid  = new (declare([Grid, ColumnResizer]))({
                    className: 'dgrid-autoheight',
                    columns: result.tableColumns
                });

                rowGrid.renderArray(result.rows);
                pane.addChild(rowGrid);
            });
        },

        initOption: function() {
            var self = this;

            var pane = registry.byId('divOption');

            // udf
            var udfList = [
                {name: 'SUBSTRING_INDEX', jar: 'SubStringIndexUDF.jar', clazz: 'com.anjuke.dw.hive.udf.SubStringIndex'},
                {name: 'RANK', jar: 'RankUDF.jar', clazz: 'com.anjuke.dw.hive.udf.Rank'},
                {name: 'MD5', jar: 'MD5UDF.jar', clazz: 'com.anjuke.dw.hive.udf.MD5'}
            ];

            var ul = '<ul class="option-list">';
            array.forEach(udfList, function(udf, i) {
                ul += '<li><label><input type="checkbox" data-dojo-type="dijit/form/CheckBox"> ' + udf.name + '</label></li>';
            });
            ul += '</ul>';

            var fsUdf = new Fieldset({
                title: 'UDF',
                toggleable: false,
                content: ul
            });
            pane.addChild(fsUdf);

            var fsSet = new Fieldset({
                title: '配置项',
                toggleable: false
            });
            pane.addChild(fsSet);
        },

        initTemplate: function() {
            var self = this;

            var store = Memory({
                data: [
                    {id: 'root'},

                    {id: 'stmt', name: '常用语句', isFolder: true, parent: 'root'},
                    {id: 'stmt-create', name: 'CREATE TABLE', isFolder: false, parent: 'stmt', content: 'CREATE TABLE db.table (\n  col1 type,\n  col2 type\n)\nPARTITIONED BY (col3 STRING)\nROW FORMAT DELIMITED FIELDS TERMINATED BY \'\\t\';\n'},
                    {id: 'stmt-insert', name: 'INSERT OVERWRITE', isFolder: false, parent: 'stmt', content: 'INSERT OVERWRITE TABLE db.table PARTITION (col3 = ${dealDate})\nAS SELECT\n  col1,\n  col2\nFROM db.table;\n'},

                    {id: 'udf', name: 'UDF', isFolder: true, parent: 'root'},
                    {id: 'udf-substring_index', name: 'SUBSTRING_INDEX', isFolder: false, parent: 'udf', content: 'ADD JAR hdfs://10.20.8.70:8020/user/hadoop/udf/SubStringIndexUDF.jar;\nCREATE TEMPORARY FUNCTION SUBSTRING_INDEX AS \'com.anjuke.dw.hive.udf.SubStringIndex\';\n'},
                    {id: 'utf-rank', name: 'RANK', isFolder: false, parent: 'udf', content: 'ADD JAR hdfs://10.20.8.70:8020/user/hadoop/udf/RankUDF.jar;\nCREATE TEMPORARY FUNCTION RANK AS \'com.anjuke.dw.hive.udf.Rank\';\n'},
                    {id: 'utf-md5', name: 'MD5', isFolder: false, parent: 'udf', content: 'ADD JAR hdfs://10.20.8.70:8020/user/hadoop/udf/MD5UDF.jar;\nCREATE TEMPORARY FUNCTION MD5 AS \'com.anjuke.dw.hive.udf.MD5\';\n'},

                    {id: 'opt', name: '优化选项', isFolder: true, parent: 'root'},
                    {id: 'opt-reducer', name: 'Reducer数量', isFolder: false, parent: 'opt', content: 'SET mapred.reducer.tasks = 20;\n'},
                    {id: 'opt-mapjoin', name: 'Map-side Join', isFolder: false, parent: 'opt', content: 'SET hive.auto.convert.join = true;\n'}
                ],
                getChildren: function(object) {
                    return this.query({parent: object.id});
                }
            });

            var model = new ObjectStoreModel({
                store: store,
                query: {id: 'root'},
                mayHaveChildren: function(item) {
                    return item.isFolder;
                },
            });

            var tree = new Tree({
                model: model,
                showRoot: false,
                onDblClick: function(item) {

                    if (item.isFolder) {
                        return;
                    }

                    var central = registry.byId('central');
                    var pane = central.selectedChildWidget;

                    if (typeof pane == 'undefined') {
                        alert('请先打开一个文档。');
                        return;
                    }

                    var editor = pane.codeMirror;
                    editor.replaceRange(item.content, editor.getCursor());
                }
            }, 'treeTemplate');
        },

        _theEnd: undefined

    });

    return new QueryEditor();

});
