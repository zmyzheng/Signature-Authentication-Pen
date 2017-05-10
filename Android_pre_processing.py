import numpy as np
import scipy.signal


def data_cleaning(data):
    pay1 = data['payload1'].split('\t')
    x = []
    y = []
    z = []
    for row in pay1[0:-1]:
        dotsplit = row.split(',')
        x.append(float(dotsplit[0][3:-1]))
        y.append(float(dotsplit[1][3:-1]))
        z.append(float(dotsplit[2][3:-2]))

    data_pro1 = np.vstack((np.array(x), np.array(y), np.array(z)))

    mean = np.mean(data_pro1, axis=1)
    dataMinusMean1 = (data_pro1.T - mean).T

    dataMinusMean1 = DownSample(dataMinusMean1) * 10
    pay2 = data['payload2'].split('\t')
    gx = []
    gy = []
    gz = []
    for row in pay2[0:-1]:
        dotsplit = row.split(',')
        gx.append(float(dotsplit[0][3:-3]))
        gy.append(float(dotsplit[1][3:-3]))
        gz.append(float(dotsplit[2][3:-4]))

    data_pro2 = np.vstack((np.array(gx), np.array(gy), np.array(gz)))

    mean = np.mean(data_pro2, axis=1)
    dataMinusMean2 = (data_pro2.T - mean).T

    dataMinusMean2 = DownSample(dataMinusMean2) * 10 

    dataMinusMean = np.vstack((dataMinusMean1,dataMinusMean2)).reshape(1,600)[0]
    return dataMinusMean
def DownSample(dataOriginal):
    t = scipy.signal.resample(dataOriginal, 100, axis=1)
    return t.reshape(1, 300)[0]
