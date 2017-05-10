from flask import Flask, request

import keras


from keras.optimizers import SGD
from keras.optimizers import RMSprop
import pandas as pd
import numpy as np
import os

import shutil
import sys
sys.path.append('../data_processing')
sys.path.append('../neural_network')
from Android_pre_processing import data_cleaning
from model import getModel

TRAIN = 0
TEST = 1
MODE = TEST

app = Flask(__name__)

# delete old train data
# with open('x_train_clean_data_android.csv', 'w') as f:
#     f.write('')

@app.route("/")
def hello():
    return "Hello World!"


if MODE == TRAIN:
    @app.route('/predict', methods=['GET', 'POST'])
    def predict():
        if request.method == 'POST':
            data = request.json
            data = data_cleaning(data)
            with open('x_train_clean_data_android.csv', 'a+') as f:
                f.write(','.join([str(d) for d in data]) + '\n')
            print(len(data))
            return "post train data"
        else:
            return "get method"
else:
    @app.route("/predict", methods=["POST"])
    def predict():
        
        if request.method == 'POST':
            data = request.json
            data = data_cleaning(data)
            x_test = np.array(data).reshape(1, 600)

            x_test = x_test.astype('float32')
            
            a = np.ones((1,300))
            b = a *230
            c = np.hstack((a,b))
            x_test = x_test / c

            #print(x_test.shape[0], 'test samples')

            model = getModel()

            model.compile(loss='categorical_crossentropy',
                          optimizer=RMSprop(),
                          metrics=['accuracy'])

            model.load_weights('../neural_network/weights3.hdf5')

            prob = model.predict(x_test)
            prediction = np.argmax(prob, axis=1)
            print(prediction)
            #print(data)
            if prediction[0] == 1:
                response = 'Mingyang Zheng'
            if prediction[0] == 0:
                response = 'Huafeng Shi'
            if prediction[0] == 2:
                response = 'Yifei Li'

        return response

if __name__ == "__main__":
    if sys.argv[1] == 'train':
        MODE = TRAIN
    if sys.argv[1] == 'test':
        MODE = TEST
    app.run(host='0.0.0.0')
